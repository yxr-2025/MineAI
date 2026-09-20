package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineai.MineAiMod;
import com.mineai.action.ActionResult;
import com.mineai.entity.AgentPlayer;
import com.mineai.llm.LlmProvider;
import com.mineai.llm.LlmResponse;
import com.mineai.llm.LlmService;
import com.mineai.llm.ToolCall;
import com.mineai.state.WorldStateCollector;
import com.mineai.tool.AgentTool;
import com.mineai.tool.ToolRegistry;
import com.mineai.tool.ToolResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Per-NPC decision loop: goal execution, dependency-aware planning, reflection
 * and a self-driven life loop.
 *
 * <p>The model decides everything: what to do, how long to rest and what was
 * learned. This class only sequences requests, applies outcomes and enforces
 * safety bounds.</p>
 */
public final class AgentController {

    private static final int THINK_TIMEOUT_TICKS = 20 * 90;
    private static final int ACTION_TIMEOUT_TICKS = 20 * 60;
    private static final int MAX_IMMEDIATE_TOOLS_PER_STEP = 16;
    private static final int MAX_LLM_ERRORS = 2;
    private static final int PLAN_STEP_COOLDOWN_TICKS = 40;
    private static final int MAX_PLAN_STEPS = 8;

    /** Objective verification: a finish claim is rejected while it is unmet. */
    private static final int MAX_OBJECTIVE_NUDGES = 4;
    /** Consecutive identical tool calls before the loop guard fires. */
    private static final int LOOP_WARN_THRESHOLD = 2;
    /** Loop warnings before forcing a re-plan or aborting. */
    private static final int MAX_LOOP_NUDGES = 2;

    /** Bounds for a model-chosen rest period. */
    private static final int MIN_REST_TICKS = 20 * 5;
    private static final int MAX_REST_TICKS = 20 * 600;
    private static final int DEFAULT_REST_TICKS = 20 * 30;
    private static final double SAFE_THREAT_RADIUS = 8.0D;

    private final AgentPlayer npc;
    private final AgentMemory memory;
    private final Plan plan = new Plan();

    private AgentState state = AgentState.IDLE;
    private String mission = "";
    private String goal = "";
    private String currentStepId;
    private String lastMessage = "";
    private String lastOutcome = "";
    private String error = "";
    private int step;
    private int maxSteps = 12;
    private int maxGoals = MAX_PLAN_STEPS;
    private int goalsCompleted;
    private int goalCooldown;
    private int restTicks;
    private int waitTicks;
    private boolean autonomous;
    private boolean lifeLoop;
    private boolean planning;
    private boolean reflecting;
    private boolean reflectionEnabled = true;
    private int reflectionMinFailures = 1;
    private int reflectionSuccessInterval = 3;
    private CompletableFuture<LlmResponse> pending;

    private final Deque<ToolCall> toolQueue = new ArrayDeque<>();
    private ToolCall activeTool;
    private final List<JsonObject> trace = new ArrayList<>();
    private BlockPos traceOrigin = BlockPos.ZERO;
    private SkillRun activeSkill;
    private int llmErrors;

    private int successStreak;
    private int failureStreak;
    private boolean goalHadFailure;

    private Objective objective = Objective.NONE;
    private Objective missionObjective = Objective.NONE;
    private int objectiveNudges;
    private int emptyResponses;
    private final Deque<String> recentCalls = new ArrayDeque<>();
    private int repeatedCalls;
    private int loopNudges;

    public AgentController(AgentPlayer npc) {
        this.npc = npc;
        this.memory = new AgentMemory(npc.npcId());
    }

    // ------------------------------------------------------------------ start

    public void start(String goal, int maxSteps) {
        start(goal, maxSteps, Objective.NONE);
    }

    public void start(String goal, int maxSteps, Objective objective) {
        reset();
        this.autonomous = false;
        this.lifeLoop = false;
        this.mission = "";
        this.goal = goal;
        this.objective = objective == null ? Objective.NONE : objective;
        this.maxSteps = Math.max(1, maxSteps);
        requestCompletion();
    }

    public void startMission(String mission, int maxSteps, int maxGoals, boolean reflection) {
        startMission(mission, maxSteps, maxGoals, reflection, Objective.NONE);
    }

    public void startMission(String mission, int maxSteps, int maxGoals, boolean reflection, Objective objective) {
        reset();
        this.autonomous = true;
        this.lifeLoop = false;
        this.mission = mission;
        this.maxSteps = Math.max(1, maxSteps);
        this.maxGoals = Math.max(1, maxGoals);
        this.reflectionEnabled = reflection;
        this.missionObjective = objective == null ? Objective.NONE : objective;
        this.objective = this.missionObjective;
        requestPlanning();
    }

    public void startLifeLoop(int maxSteps, int maxGoals, boolean reflection) {
        reset();
        this.autonomous = true;
        this.lifeLoop = true;
        this.mission = "Stay alive, keep yourself supplied and make yourself useful.";
        this.maxSteps = Math.max(1, maxSteps);
        this.maxGoals = Math.max(1, maxGoals);
        this.reflectionEnabled = reflection;
        requestPlanning();
    }

    public void configureReflection(int minFailures, int successInterval) {
        this.reflectionMinFailures = Math.max(1, minFailures);
        this.reflectionSuccessInterval = Math.max(1, successInterval);
    }

    private void reset() {
        stop();
        memory.clearHistory();
        this.step = 0;
        this.lastMessage = "";
        this.lastOutcome = "";
        this.error = "";
        this.currentStepId = null;
        this.goalsCompleted = 0;
        this.goalCooldown = 0;
        this.restTicks = 0;
        this.pending = null;
        this.planning = false;
        this.reflecting = false;
        this.llmErrors = 0;
        this.successStreak = 0;
        this.failureStreak = 0;
        this.goalHadFailure = false;
        this.objective = Objective.NONE;
        this.missionObjective = Objective.NONE;
        this.objectiveNudges = 0;
        this.emptyResponses = 0;
        this.recentCalls.clear();
        this.repeatedCalls = 0;
        this.loopNudges = 0;
        this.traceOrigin = npc.blockPosition();
        this.activeSkill = null;
        this.trace.clear();
        this.plan.clear();
        this.toolQueue.clear();
        this.activeTool = null;
    }

    public void stop() {
        state = AgentState.IDLE;
        pending = null;
        planning = false;
        reflecting = false;
        toolQueue.clear();
        activeTool = null;
        npc.actionQueue().clear(npc);
        npc.mover().stop();
        memory.save();
    }

    // ------------------------------------------------------------------- tick

    public void tick() {
        if (goalCooldown > 0) {
            goalCooldown--;
            return;
        }
        if (state == AgentState.IDLE) {
            if (!lifeLoop) {
                return;
            }
            if (restTicks > 0 && !unsafeNow()) {
                restTicks--;
                return;
            }
            restTicks = 0;
            requestPlanning();
            return;
        }
        switch (state) {
            case THINKING -> tickThinking();
            case AWAITING_ACTION -> tickAwaitingAction();
            case RUNNING_SKILL -> tickSkill();
            default -> {
            }
        }
    }

    /**
     * Safety reflex (not a decision): never sleep through danger.
     */
    private boolean unsafeNow() {
        if (npc.getHealth() < npc.getMaxHealth() * 0.3F) {
            return true;
        }
        if (npc.getAirSupply() < 60) {
            return true;
        }
        for (Entity entity : npc.level().getEntities(npc, npc.getBoundingBox().inflate(SAFE_THREAT_RADIUS))) {
            if (entity instanceof Enemy) {
                return true;
            }
        }
        return false;
    }

    public boolean isRunning() {
        return state == AgentState.THINKING || state == AgentState.AWAITING_ACTION;
    }

    public AgentState state() {
        return state;
    }

    public String goal() {
        return goal;
    }

    public String mission() {
        return mission;
    }

    public String lastMessage() {
        return lastMessage;
    }

    public String error() {
        return error;
    }

    public int step() {
        return step;
    }

    public int goalsCompleted() {
        return goalsCompleted;
    }

    public boolean isLifeLoop() {
        return lifeLoop;
    }

    public int planDone() {
        return plan.doneCount();
    }

    public int planSize() {
        return plan.size();
    }

    public int restSeconds() {
        return restTicks / 20;
    }

    public AgentMemory memory() {
        return memory;
    }

    public Objective currentObjective() {
        return objective;
    }

    // ---------------------------------------------------------------- requests

    private void requestCompletion() {
        LlmProvider provider = LlmService.provider();
        if (provider == null) {
            fail("LLM provider is not configured");
            return;
        }
        List<JsonObject> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt()));
        messages.addAll(memory.history());

        planning = false;
        reflecting = false;
        state = AgentState.THINKING;
        waitTicks = 0;
        try {
            pending = provider.complete(messages, ToolRegistry.schema());
        } catch (RuntimeException exception) {
            fail("failed to start LLM call: " + exception.getMessage());
        }
    }

    private void requestPlanning() {
        LlmProvider provider = LlmService.planner();
        if (provider == null) {
            fail("LLM provider is not configured");
            return;
        }
        List<JsonObject> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildPlannerPrompt()));
        messages.addAll(memory.history());

        planning = true;
        reflecting = false;
        state = AgentState.THINKING;
        waitTicks = 0;
        try {
            pending = provider.complete(messages, plannerTools());
        } catch (RuntimeException exception) {
            fail("failed to start planning call: " + exception.getMessage());
        }
    }

    private void requestReflection(String outcome) {
        successStreak = 0;
        failureStreak = 0;
        if (!reflectionEnabled) {
            continueAfterGoal(goalHadFailure);
            return;
        }
        LlmProvider provider = LlmService.planner();
        if (provider == null) {
            fail("LLM provider is not configured");
            return;
        }
        this.lastOutcome = outcome;
        List<JsonObject> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildReflectionPrompt()));
        messages.addAll(memory.history());

        reflecting = true;
        planning = false;
        state = AgentState.THINKING;
        waitTicks = 0;
        try {
            pending = provider.complete(messages, reflectionTools());
        } catch (RuntimeException exception) {
            fail("failed to start reflection call: " + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------- ticks

    private void tickThinking() {
        if (pending == null) {
            return;
        }
        if (!pending.isDone()) {
            if (++waitTicks > THINK_TIMEOUT_TICKS) {
                pending = null;
                fail("LLM call timed out");
            }
            return;
        }

        LlmResponse response;
        try {
            response = pending.join();
        } catch (RuntimeException exception) {
            pending = null;
            waitTicks = 0;
            llmErrors++;
            if (llmErrors <= MAX_LLM_ERRORS) {
                memory.addHistory(ChatMessage.user(
                        "The previous model request failed (" + rootMessage(exception)
                                + "). Continue: retry the last step or choose a different approach."));
                requestCompletion();
                return;
            }
            fail("LLM call failed: " + rootMessage(exception));
            return;
        }
        pending = null;
        waitTicks = 0;
        llmErrors = 0;

        if (planning) {
            handlePlanningResponse(response);
            return;
        }
        if (reflecting) {
            handleReflectionResponse(response);
            return;
        }

        step++;
        if (response.content() != null && !response.content().isBlank()) {
            lastMessage = response.content();
        }
        MineAiMod.LOGGER.info("[mineai] agent {} step {} content={} toolCalls={}",
                npc.npcId(), step, abbreviate(response.content()), response.toolCalls().size());

        memory.addHistory(ChatMessage.assistant(response));

        if (!response.hasToolCalls()) {
            boolean empty = response.content() == null || response.content().isBlank();
            if (empty && emptyResponses < 2) {
                emptyResponses++;
                memory.addHistory(ChatMessage.user(
                        "Your last message was empty. You must reply with exactly one tool call to make progress; "
                                + "do not reply with prose or nothing."));
                MineAiMod.LOGGER.info("[mineai] agent {} empty response, re-prompting ({}/2)",
                        npc.npcId(), emptyResponses);
                requestCompletion();
                return;
            }
            finishGoal(lastMessage.isBlank() ? "goal finished" : lastMessage, goalHadFailure);
            return;
        }
        emptyResponses = 0;

        toolQueue.clear();
        toolQueue.addAll(response.toolCalls());
        processNextToolCall();
    }

    private void handlePlanningResponse(LlmResponse response) {
        planning = false;
        MineAiMod.LOGGER.info("[mineai] agent {} planning response: content={} tools={}",
                npc.npcId(), abbreviate(response.content()),
                response.toolCalls().stream().map(ToolCall::name).toList());
        memory.addHistory(ChatMessage.assistant(response));

        ToolCall setPlan = null;
        ToolCall rest = null;
        ToolCall complete = null;
        for (ToolCall call : response.toolCalls()) {
            switch (call.name()) {
                case "set_plan" -> setPlan = call;
                case "rest" -> rest = call;
                case "mission_complete" -> complete = call;
                default -> {
                }
            }
        }

        if (setPlan != null) {
            memory.addHistory(ChatMessage.tool(setPlan.id(), "{\"success\":true}"));
            List<Plan.Step> steps = parsePlan(setPlan.arguments(), maxGoals);
            if (steps.isEmpty()) {
                finishMission("planner returned an empty plan");
                return;
            }
            if (Plan.hasCycle(steps)) {
                memory.addHistory(ChatMessage.user("The plan had a dependency cycle; rewrite it without cycles."));
                requestPlanning();
                return;
            }
            plan.set(steps);
            MineAiMod.LOGGER.info("[mineai] agent {} new plan with {} step(s):\n{}",
                    npc.npcId(), plan.size(), plan.summary());
            startNextStep();
            return;
        }

        if (rest != null) {
            int seconds = rest.arguments().has("seconds")
                    ? (int) rest.arguments().get("seconds").getAsDouble()
                    : DEFAULT_REST_TICKS / 20;
            restTicks = clampRest(seconds);
            memory.addHistory(ChatMessage.tool(rest.id(), "{\"success\":true}"));
            MineAiMod.LOGGER.info("[mineai] agent {} resting {}s", npc.npcId(), restTicks / 20);
            state = AgentState.IDLE;
            return;
        }

        if (complete != null) {
            memory.addHistory(ChatMessage.tool(complete.id(), "{\"success\":true}"));
        }
        if (lifeLoop) {
            restTicks = DEFAULT_REST_TICKS;
            state = AgentState.IDLE;
        } else {
            finishMission(lastMessage.isBlank() ? "mission complete" : lastMessage);
        }
    }

    private void handleReflectionResponse(LlmResponse response) {
        reflecting = false;
        memory.addHistory(ChatMessage.assistant(response));

        ToolCall lessonCall = null;
        for (ToolCall call : response.toolCalls()) {
            if ("record_lesson".equals(call.name())) {
                lessonCall = call;
            }
        }

        boolean success = !goalHadFailure;
        if (lessonCall != null) {
            JsonObject args = lessonCall.arguments();
            String lesson = args.has("lesson") ? args.get("lesson").getAsString() : "";
            if (args.has("success")) {
                success = args.get("success").getAsBoolean();
            }
            if (!lesson.isBlank()) {
                memory.addLesson(lesson);
                MineAiMod.LOGGER.info("[mineai] agent {} lesson ({}): {}", npc.npcId(),
                        success ? "success" : "failure", lesson);
            }
            memory.addHistory(ChatMessage.tool(lessonCall.id(), "{\"success\":true}"));
        }
        memory.save();
        continueAfterGoal(!success);
    }

    private void continueAfterGoal(boolean failed) {
        // Stop as soon as the mission objective is satisfied, even if the plan
        // still has steps left.
        if (!failed && objectiveMet(missionObjective)) {
            finishMission("mission objective met");
            return;
        }
        if (failed) {
            if (autonomous) {
                MineAiMod.LOGGER.info("[mineai] agent {} re-planning after failure", npc.npcId());
                goalCooldown = PLAN_STEP_COOLDOWN_TICKS;
                requestPlanning();
                return;
            }
            state = AgentState.FINISHED;
            return;
        }
        startNextStep();
    }

    private void startNextStep() {
        Plan.Step next = plan.next();
        if (next == null) {
            if (plan.hasPending()) {
                // remaining steps are blocked by a failed dependency
                MineAiMod.LOGGER.info("[mineai] agent {} plan blocked, re-planning", npc.npcId());
                requestPlanning();
                return;
            }
            // Plan exhausted: finish only if the mission objective is met,
            // otherwise reflect once and re-plan. Looping straight back into
            // reflection here would spin forever.
            if (objectiveMet(missionObjective)) {
                finishMission(lastOutcome.isBlank() ? "plan complete" : lastOutcome);
                return;
            }
            if (objectiveNudges > 0) {
                objectiveNudges = 0;
                MineAiMod.LOGGER.info("[mineai] agent {} plan exhausted, objective unmet, re-planning",
                        npc.npcId());
                goalCooldown = PLAN_STEP_COOLDOWN_TICKS;
                requestPlanning();
                return;
            }
            requestReflection(lastOutcome.isBlank() ? "plan complete" : lastOutcome);
            return;
        }
        currentStepId = next.id();
        goal = next.goal();
        goalHadFailure = false;
        // Each plan step gets its own step budget and its own objective.
        step = 0;
        objectiveNudges = 0;
        loopNudges = 0;
        recentCalls.clear();
        repeatedCalls = 0;
        Objective stepObjective = next.objective() == null ? Objective.NONE : next.objective();
        objective = stepObjective.isSet() ? stepObjective : missionObjective;
        MineAiMod.LOGGER.info("[mineai] agent {} plan step {} ({} done): {}",
                npc.npcId(), next.id(), plan.doneCount(), goal);
        requestCompletion();
    }

    private void processNextToolCall() {
        int guard = 0;
        while (true) {
            if (++guard > MAX_IMMEDIATE_TOOLS_PER_STEP) {
                fail("too many tool calls in a single step");
                return;
            }

            ToolCall call = toolQueue.pollFirst();
            if (call == null) {
                if (step >= maxSteps) {
                    finishGoal("reached max steps; last message: " + lastMessage, true);
                    return;
                }
                requestCompletion();
                return;
            }

            AgentTool tool = ToolRegistry.get(call.name());
            if (tool == null) {
                memory.addHistory(ChatMessage.tool(call.id(), errorJson("unknown tool: " + call.name())));
                goalHadFailure = true;
                continue;
            }

            if (noteCall(call)) {
                loopNudges++;
                memory.addHistory(ChatMessage.tool(call.id(), errorJson(
                        "the same call was repeated " + (repeatedCalls + 1) + " times with no progress")));
                repeatedCalls = 0;
                if (loopNudges > MAX_LOOP_NUDGES) {
                    loopNudges = 0;
                    if (autonomous) {
                        MineAiMod.LOGGER.info("[mineai] agent {} loop detected, re-planning", npc.npcId());
                        goalCooldown = PLAN_STEP_COOLDOWN_TICKS;
                        requestPlanning();
                    } else {
                        fail("stuck repeating the same action");
                    }
                    return;
                }
                memory.addHistory(ChatMessage.user(
                        "You are looping on the same call. Stop and choose a different approach."));
                requestCompletion();
                return;
            }

            ToolResult result;
            try {
                result = tool.execute(npc, call.arguments());
            } catch (RuntimeException exception) {
                memory.addHistory(ChatMessage.tool(call.id(), errorJson("tool error: " + exception.getMessage())));
                goalHadFailure = true;
                continue;
            }

            MineAiMod.LOGGER.info("[mineai] agent {} tool {} -> {}", npc.npcId(), call.name(), result.message());
            recordTrace(call);
            if (!result.success()) {
                goalHadFailure = true;
            }

            if (result.queued()) {
                activeTool = call;
                waitTicks = 0;
                state = activeSkill != null ? AgentState.RUNNING_SKILL : AgentState.AWAITING_ACTION;
                return;
            }

            memory.addHistory(ChatMessage.tool(call.id(), result.toToolContent()));
            if (result.imageBase64() != null) {
                memory.addHistory(ChatMessage.image(result.imageBase64(),
                        "Image produced by the " + call.name() + " tool."));
            }
        }
    }

    private void tickAwaitingAction() {
        if (activeTool == null) {
            processNextToolCall();
            return;
        }
        if (++waitTicks > ACTION_TIMEOUT_TICKS) {
            memory.addHistory(ChatMessage.tool(activeTool.id(), errorJson("action timed out")));
            goalHadFailure = true;
            activeTool = null;
            processNextToolCall();
            return;
        }
        if (npc.actionQueue().isBusy()) {
            return;
        }

        ActionResult actionResult = npc.actionQueue().lastResult();
        boolean success = actionResult == ActionResult.SUCCESS;
        if (!success) {
            goalHadFailure = true;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("success", success);
        payload.addProperty("message", success
                ? "action completed"
                : "action failed: " + (actionResult == null ? "unknown" : actionResult.name()));
        memory.addHistory(ChatMessage.tool(activeTool.id(), payload.toString()));
        activeTool = null;
        processNextToolCall();
    }

    /**
     * Deterministic skill replay. Steps run back to back from the server tick;
     * the model is only consulted again if a step fails.
     */
    private void tickSkill() {
        SkillRun run = activeSkill;
        if (run == null) {
            processNextToolCall();
            return;
        }
        if (run.tick() > ACTION_TIMEOUT_TICKS * Math.max(1, run.size()) + 200) {
            abortSkill("timed out");
            return;
        }
        if (npc.actionQueue().isBusy()) {
            return;
        }
        if (run.isAwaitingAction()) {
            run.setAwaitingAction(false);
            ActionResult actionResult = npc.actionQueue().lastResult();
            if (actionResult != ActionResult.SUCCESS) {
                abortSkill("step " + run.index() + " failed: "
                        + (actionResult == null ? "unknown" : actionResult.name()));
                return;
            }
        }

        while (run.hasNext()) {
            JsonObject step = run.nextStep();
            String toolName = step.has("tool") ? step.get("tool").getAsString() : "";
            JsonObject args = step.has("args") ? step.getAsJsonObject("args").deepCopy() : new JsonObject();
            translate(args, run);

            AgentTool tool = ToolRegistry.get(toolName);
            if (tool == null) {
                continue;
            }
            ToolResult result;
            try {
                result = tool.execute(npc, args);
            } catch (RuntimeException exception) {
                abortSkill("step " + run.index() + " threw: " + exception.getMessage());
                return;
            }
            if (!result.success()) {
                abortSkill("step " + run.index() + " (" + toolName + ") failed: " + result.message());
                return;
            }
            if (result.queued()) {
                run.setAwaitingAction(true);
                return;
            }
        }
        finishSkill();
    }

    private void translate(JsonObject args, SkillRun run) {
        if (args.has("x")) {
            args.addProperty("x", run.shiftX(args));
        }
        if (args.has("y")) {
            args.addProperty("y", run.shiftY(args));
        }
        if (args.has("z")) {
            args.addProperty("z", run.shiftZ(args));
        }
    }

    private void abortSkill(String reason) {
        SkillRun run = activeSkill;
        activeSkill = null;
        String name = run == null ? "?" : run.name();
        MineAiMod.LOGGER.info("[mineai] agent {} skill '{}' aborted: {}", npc.npcId(), name, reason);
        // Classify the failure and hand back a targeted repair hint.
        String hint = FailureClassifier.hintFor(npc, objective, reason);
        memory.addHistory(ChatMessage.tool(toolCallId(), errorJson(
                "program '" + name + "' aborted at step " + (run == null ? "?" : run.index())
                        + ": " + reason + ". Repair: " + hint)));
        activeTool = null;
        processNextToolCall();
    }

    private void finishSkill() {
        SkillRun run = activeSkill;
        activeSkill = null;
        String name = run == null ? "?" : run.name();
        if (run != null) {
            memory.recordUse(name);
            memory.save();
        }
        MineAiMod.LOGGER.info("[mineai] agent {} skill '{}' completed", npc.npcId(), name);
        JsonObject payload = new JsonObject();
        payload.addProperty("success", true);
        payload.addProperty("message", "skill '" + name + "' completed");
        memory.addHistory(ChatMessage.tool(toolCallId(), payload.toString()));
        activeTool = null;
        processNextToolCall();
    }

    private String toolCallId() {
        return activeTool == null ? "skill" : activeTool.id();
    }

    // -------------------------------------------------------------- lifecycle

    private void finishGoal(String message, boolean failed) {
        // The model does not get to decide that it is done: the objective is
        // checked against the world first.
        if (!objectiveMet(objective)) {
            objectiveNudges++;
            if (objectiveNudges <= MAX_OBJECTIVE_NUDGES) {
                // Grant a fresh step budget and keep going; the model does not
                // get to end the goal while the objective is unmet.
                step = 0;
                emptyResponses = 0;
                memory.addHistory(ChatMessage.user(
                        "Objective not met. You must hold " + objective.describe()
                                + " but you currently have " + objectiveHave(objective)
                                + ". Do not finish. Reply with exactly one tool call that makes progress, "
                                + "for example equip the right tool and mine the next block you need."));
                MineAiMod.LOGGER.info("[mineai] agent {} finish rejected, objective unmet ({}/{}), nudge {}",
                        npc.npcId(), objectiveHave(objective), objective.count(), objectiveNudges);
                requestCompletion();
                return;
            }
            MineAiMod.LOGGER.info("[mineai] agent {} objective still unmet after {} nudges",
                    npc.npcId(), MAX_OBJECTIVE_NUDGES);
            failed = true;
        }

        lastMessage = message;
        lastOutcome = message;
        goalsCompleted++;
        memory.save();

        if (currentStepId != null) {
            if (failed) {
                plan.markFailed(currentStepId);
            } else {
                plan.markDone(currentStepId);
            }
            currentStepId = null;
        }
        MineAiMod.LOGGER.info("[mineai] agent {} goal done ({}): {}",
                npc.npcId(), failed ? "failed" : "ok", abbreviate(message));

        if (!autonomous) {
            state = AgentState.FINISHED;
            return;
        }

        // Objective reached: stop now instead of running the rest of the plan.
        if (!failed && objectiveMet(missionObjective)) {
            finishMission("mission objective met");
            return;
        }

        if (failed) {
            failureStreak++;
            successStreak = 0;
        } else {
            successStreak++;
            failureStreak = 0;
        }
        boolean shouldReflect = failed
                ? failureStreak >= reflectionMinFailures
                : successStreak >= reflectionSuccessInterval;

        if (shouldReflect) {
            requestReflection(message);
        } else {
            continueAfterGoal(failed);
        }
    }

    private void finishMission(String message) {
        if (!objectiveMet(missionObjective)) {
            objectiveNudges++;
            if (objectiveNudges <= MAX_OBJECTIVE_NUDGES) {
                step = 0;
                emptyResponses = 0;
                memory.addHistory(ChatMessage.user(
                        "The mission objective is still unmet: " + missionObjective.describe()
                                + " (you have " + objectiveHave(missionObjective) + "). "
                                + "Do not finish; plan and execute the remaining steps."));
                MineAiMod.LOGGER.info("[mineai] agent {} mission finish rejected, objective unmet ({}/{})",
                        npc.npcId(), objectiveHave(missionObjective), missionObjective.count());
                goalCooldown = PLAN_STEP_COOLDOWN_TICKS;
                requestPlanning();
                return;
            }
        }
        lastMessage = message;
        memory.save();
        state = AgentState.FINISHED;
        MineAiMod.LOGGER.info("[mineai] agent {} mission finished: {}", npc.npcId(), message);
    }

    private void fail(String message) {
        error = message;
        state = AgentState.FAILED;
        MineAiMod.LOGGER.warn("[mineai] agent {} failed: {}", npc.npcId(), message);
    }

    // ------------------------------------------------------------------ memory

    /**
     * Tracks consecutive identical tool calls. Repeating the same call with
     * the same arguments means no progress, so the loop guard intervenes.
     */
    private boolean noteCall(ToolCall call) {
        String key = call.name() + "|" + call.arguments();
        if (!recentCalls.isEmpty() && recentCalls.peekLast().equals(key)) {
            repeatedCalls++;
        } else {
            repeatedCalls = 0;
        }
        recentCalls.addLast(key);
        while (recentCalls.size() > 16) {
            recentCalls.removeFirst();
        }
        return repeatedCalls >= LOOP_WARN_THRESHOLD;
    }

    private void recordTrace(ToolCall call) {
        JsonObject entry = new JsonObject();
        entry.addProperty("tool", call.name());
        entry.add("args", call.arguments());
        trace.add(entry);
    }

    public ToolResult saveCurrentTraceAsSkill(String name, String description) {
        List<JsonObject> executable = Skill.executableSteps(trace);
        if (executable.isEmpty()) {
            return ToolResult.fail("no replayable steps recorded yet");
        }
        // Reject obviously broken programs before they are stored.
        SkillValidator.Result validation = SkillValidator.validate(executable);
        if (!validation.valid()) {
            return ToolResult.fail("skill not saved: " + validation.describe());
        }
        BlockPos origin = traceOrigin == null ? npc.blockPosition() : traceOrigin;
        memory.putSkill(new Skill(name, description, executable, 0,
                origin.getX(), origin.getY(), origin.getZ()));
        memory.save();
        return ToolResult.ok("saved executable skill '" + name + "' with " + executable.size() + " steps");
    }

    /**
     * Starts deterministic replay of a skill. No model call happens between
     * steps; the whole procedure runs from the server tick.
     */
    public ToolResult startSkill(String name, BlockPos anchor) {
        Skill skill = memory.skill(name);
        if (skill == null) {
            return ToolResult.fail("no skill named " + name);
        }
        if (skill.steps().isEmpty()) {
            return ToolResult.fail("skill '" + name + "' has no executable steps");
        }
        return startProgram(name, skill.steps(),
                new BlockPos(skill.originX(), skill.originY(), skill.originZ()),
                anchor == null ? npc.blockPosition() : anchor);
    }

    /**
     * Runs an arbitrary generated program through the same deterministic
     * executor as recorded skills.
     */
    public ToolResult startProgram(String name, List<JsonObject> steps, BlockPos origin, BlockPos anchor) {
        if (steps == null || steps.isEmpty()) {
            return ToolResult.fail("procedure '" + name + "' produced no steps");
        }
        this.activeSkill = new SkillRun(name, steps, origin, anchor);
        MineAiMod.LOGGER.info("[mineai] agent {} running program '{}' ({} steps) at {}",
                npc.npcId(), name, steps.size(), anchor);
        return ToolResult.queued("running '" + name + "' ("
                + steps.size() + " steps, no further decisions needed)");
    }

    public ToolResult recallSkill(String name) {
        Skill skill = memory.skill(name);
        if (skill == null) {
            return ToolResult.fail("no skill named " + name);
        }
        memory.recordUse(name);
        memory.save();

        JsonObject data = new JsonObject();
        data.addProperty("name", skill.name());
        data.addProperty("description", skill.description());
        data.addProperty("uses", skill.uses());
        JsonArray steps = new JsonArray();
        for (JsonObject step : skill.steps()) {
            steps.add(step);
        }
        data.add("steps", steps);
        return ToolResult.okData("recalled skill '" + name + "'", data);
    }

    /**
     * Sets the checkable end state. The controller verifies it, so the model
     * cannot end a goal by simply claiming success.
     */
    public ToolResult declareObjective(String itemId, int count) {
        if (com.mineai.util.RegistryIds.itemById(itemId) == null) {
            return ToolResult.fail("unknown item: " + itemId);
        }
        this.objective = Objective.haveItem(itemId, count);
        this.objectiveNudges = 0;
        MineAiMod.LOGGER.info("[mineai] agent {} objective set: {}", npc.npcId(), objective.describe());
        return ToolResult.ok("objective set: " + objective.describe());
    }

    private boolean objectiveMet(Objective target) {
        if (target == null || !target.isSet()) {
            return true;
        }
        net.minecraft.world.item.Item item = com.mineai.util.RegistryIds.itemById(target.itemId());
        if (item == null) {
            return true;
        }
        return com.mineai.util.InventoryHelper.count(npc, item) >= target.count();
    }

    private int objectiveHave(Objective target) {
        if (target == null) {
            return 0;
        }
        net.minecraft.world.item.Item item = com.mineai.util.RegistryIds.itemById(target.itemId());
        return item == null ? 0 : com.mineai.util.InventoryHelper.count(npc, item);
    }

    public ToolResult remember(String note) {
        memory.addNote(note);
        memory.save();
        return ToolResult.ok("remembered: " + note);
    }

    // ----------------------------------------------------------------- prompts

    private String buildSystemPrompt() {
        JsonObject full = WorldStateCollector.collect(npc);
        JsonObject compact = new JsonObject();
        compact.add("self", full.get("self"));
        compact.add("inventory", full.get("inventory"));
        compact.add("environment", full.get("environment"));

        StringBuilder builder = new StringBuilder();
        builder.append("You are MineAI, an autonomous Minecraft agent controlling the player \"")
                .append(npc.getName().getString()).append("\".\n");
        builder.append("You can only affect the world through the provided tools. Prefer one tool call per step.\n");
        builder.append("Coordinates are absolute block coordinates and the reach limit is about 6 blocks.\n");
        builder.append("Mining and placing are realistic: the agent walks to the target and digs over time.\n");
        builder.append("Use scan_area for terrain and structures, collect_state for a full snapshot, ")
                .append("query_block for a single block, and list_container for containers.\n");
        builder.append("Hold the right item with equip before mining, attacking or eating; ")
                .append("mine faster with a better tool and attack only when your attack is charged.\n");
        builder.append("A broken block drops its item where the block was, and you must walk onto it to pick it up. ")
                .append("Chop a tree from its lowest log so the drops land on reachable ground; ")
                .append("never mine from high in the canopy or the drops will strand on the leaves.\n");

        if (!mission.isBlank()) {
            builder.append("Long-term mission: ").append(mission).append('\n');
        }
        if (!plan.isEmpty()) {
            builder.append("Plan (your step is marked):\n").append(plan.summary());
        }
        builder.append("Current goal: ").append(goal).append('\n');
        if (objective.isSet()) {
            builder.append("Objective (verified by the system, you cannot end the goal until it is true): ")
                    .append(objective.describe()).append('\n');
            net.minecraft.world.item.Item target = com.mineai.util.RegistryIds.itemById(objective.itemId());
            if (target != null) {
                builder.append("Recipe for the objective item: ")
                        .append(RecipeLookup.summarize(npc.level(), target)).append('\n');
                builder.append("Call missing_for to see exactly what is still missing.\n");
            }
        }
        builder.append("Current state: ").append(compact).append('\n');
        builder.append("Candidate actions right now: ")
                .append(CandidateActions.build(npc, objective)).append('\n');
        builder.append("Prefer: run a ready candidate with process, or run_skill a known skill; ")
                .append("only fall back to manual goto/mine/craft when nothing fits.\n");

        if (!memory.notes().isEmpty()) {
            builder.append("Notes from earlier sessions:\n");
            for (String note : memory.notes()) {
                builder.append("- ").append(note).append('\n');
            }
        }
        if (!memory.lessons().isEmpty()) {
            builder.append("Lessons you learned (avoid repeating mistakes):\n");
            for (String lesson : memory.lessons()) {
                builder.append("- ").append(lesson).append('\n');
            }
        }
        if (!memory.skills().isEmpty()) {
            builder.append("Known skills (use recall_skill to see the steps):\n");
            for (Skill skill : memory.skills()) {
                builder.append("- ").append(skill.summary()).append('\n');
            }
        }
        if (!SharedBlackboard.all().isEmpty()) {
            builder.append("Shared blackboard (facts from other agents):\n");
            for (SharedBlackboard.Entry entry : SharedBlackboard.all()) {
                builder.append("- ").append(entry.key()).append(" = ").append(entry.value())
                        .append(" (by ").append(entry.author()).append(")\n");
            }
        }

        builder.append("After changing the world, verify the result with a read tool before finishing. ")
                .append("Save a procedure worth repeating with save_skill; running it with run_skill replays the ")
                .append("whole procedure without further decisions, so prefer run_skill over doing it step by step. ")
                .append("Publish useful discoveries with share. ")
                .append("When the goal is complete, reply with a short summary and no tool calls.");
        return builder.toString();
    }

    private String buildPlannerPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the planner for MineAI, an autonomous Minecraft agent.\n");
        builder.append("Long-term mission: ").append(mission).append('\n');
        builder.append("Goals completed so far: ").append(goalsCompleted).append('\n');
        builder.append("Current needs: ").append(AgentNeeds.snapshot(npc)).append('\n');
        if (objective.isSet()) {
            builder.append("Objective (verified by the system): ").append(objective.describe()).append('\n');
            net.minecraft.world.item.Item target = com.mineai.util.RegistryIds.itemById(objective.itemId());
            if (target != null) {
                builder.append("Recipe for the objective item: ")
                        .append(RecipeLookup.summarize(npc.level(), target)).append('\n');
                builder.append("Call missing_for for the current gap list.\n");
            }
        }
        if (!plan.isEmpty()) {
            builder.append("Previous plan and status:\n").append(plan.summary());
        }
        if (!lastOutcome.isBlank()) {
            builder.append("Last outcome: ").append(lastOutcome).append('\n');
        }
        if (!memory.notes().isEmpty()) {
            builder.append("Notes: ").append(memory.notes()).append('\n');
        }
        if (!memory.lessons().isEmpty()) {
            builder.append("Lessons:\n");
            for (String lesson : memory.lessons()) {
                builder.append("- ").append(lesson).append('\n');
            }
        }
        if (!memory.skills().isEmpty()) {
            builder.append("Known skills:\n");
            for (Skill skill : memory.skills()) {
                builder.append("- ").append(skill.summary()).append('\n');
            }
        }
        if (!SharedBlackboard.all().isEmpty()) {
            builder.append("Shared blackboard:\n");
            for (SharedBlackboard.Entry entry : SharedBlackboard.all()) {
                builder.append("- ").append(entry.key()).append(" = ").append(entry.value()).append('\n');
            }
        }
        builder.append("Write a short ordered plan of concrete steps that makes progress on the mission, ")
                .append("taking needs and lessons into account. Each step has an id, a goal, and may list depends_on ids. ")
                .append("Use recipe_for and missing_for to look up how an item is made and what is still missing. ")
                .append("Call set_plan with the steps. If nothing needs doing right now, call rest with how many ")
                .append("seconds to wait before re-checking (5 to 600). Call mission_complete only when the ")
                .append("mission is truly finished.");
        return builder.toString();
    }

    private String buildReflectionPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the reflection step for MineAI.\n");
        builder.append("Just finished goal: ").append(goal).append('\n');
        builder.append("Result reported by the agent: ").append(lastOutcome).append('\n');
        builder.append("Call record_lesson with one short, concrete lesson learned ")
                .append("(what worked, what failed, what to do differently) and whether the goal succeeded.");
        return builder.toString();
    }

    private static JsonArray plannerTools() {
        JsonArray tools = new JsonArray();

        JsonObject planProperties = new JsonObject();
        JsonObject steps = new JsonObject();
        steps.addProperty("type", "array");
        steps.addProperty("description", "ordered list of steps; each has goal, an optional id and optional depends_on");
        JsonObject items = new JsonObject();
        items.addProperty("type", "object");
        JsonObject itemProperties = new JsonObject();
        JsonObject id = new JsonObject();
        id.addProperty("type", "string");
        id.addProperty("description", "short step id");
        itemProperties.add("id", id);
        JsonObject goalProperty = new JsonObject();
        goalProperty.addProperty("type", "string");
        goalProperty.addProperty("description", "what to accomplish");
        itemProperties.add("goal", goalProperty);
        JsonObject depends = new JsonObject();
        depends.addProperty("type", "array");
        depends.addProperty("description", "ids of steps that must finish first");
        JsonObject dependsItems = new JsonObject();
        dependsItems.addProperty("type", "string");
        depends.add("items", dependsItems);
        itemProperties.add("depends_on", depends);
        items.add("properties", itemProperties);
        steps.add("items", items);
        planProperties.add("steps", steps);
        tools.add(AgentTool.schema("set_plan", "Set the ordered, dependency-aware plan of sub-goals.",
                planProperties, "steps"));

        JsonObject restProperties = new JsonObject();
        JsonObject seconds = new JsonObject();
        seconds.addProperty("type", "number");
        seconds.addProperty("description", "seconds to wait before re-checking needs, 5 to 600");
        restProperties.add("seconds", seconds);
        JsonObject reason = new JsonObject();
        reason.addProperty("type", "string");
        reason.addProperty("description", "why nothing needs doing now");
        restProperties.add("reason", reason);
        tools.add(AgentTool.schema("rest", "Wait and re-check later instead of acting now.",
                restProperties, "seconds"));

        tools.add(AgentTool.schema("mission_complete", "Call when the overall mission is finished.",
                new JsonObject()));
        return tools;
    }

    private static JsonArray reflectionTools() {
        JsonArray tools = new JsonArray();
        JsonObject properties = new JsonObject();

        JsonObject lesson = new JsonObject();
        lesson.addProperty("type", "string");
        lesson.addProperty("description", "one short concrete lesson");
        properties.add("lesson", lesson);

        JsonObject success = new JsonObject();
        success.addProperty("type", "boolean");
        success.addProperty("description", "whether the goal succeeded");
        properties.add("success", success);

        tools.add(AgentTool.schema("record_lesson", "Record what was learned from this goal.", properties,
                "lesson", "success"));
        return tools;
    }

    // ------------------------------------------------------------------ helpers

    private static List<Plan.Step> parsePlan(JsonObject args, int maxSteps) {
        List<Plan.Step> steps = new ArrayList<>();
        if (args == null || !args.has("steps")) {
            return steps;
        }
        JsonElement element = args.get("steps");
        if (!element.isJsonArray()) {
            return steps;
        }
        int index = 0;
        for (JsonElement entry : element.getAsJsonArray()) {
            if (steps.size() >= maxSteps) {
                break;
            }
            index++;
            String id;
            String goal;
            List<String> dependsOn = new ArrayList<>();
            Objective stepObjective = Objective.NONE;
            if (entry.isJsonPrimitive()) {
                id = String.valueOf(index);
                goal = entry.getAsString();
            } else if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                id = object.has("id") ? object.get("id").getAsString() : String.valueOf(index);
                goal = object.has("goal") ? object.get("goal").getAsString() : "";
                if (object.has("depends_on") && object.get("depends_on").isJsonArray()) {
                    for (JsonElement dep : object.getAsJsonArray("depends_on")) {
                        dependsOn.add(dep.getAsString());
                    }
                }
                if (object.has("objective") && object.get("objective").isJsonObject()) {
                    stepObjective = Objective.fromJson(object.getAsJsonObject("objective"));
                }
            } else {
                continue;
            }
            if (goal.isBlank()) {
                continue;
            }
            steps.add(new Plan.Step(id, goal, dependsOn, stepObjective, Plan.Status.PENDING));
        }
        return steps;
    }

    private static int clampRest(int seconds) {
        int ticks = seconds * 20;
        if (ticks < MIN_REST_TICKS) {
            return MIN_REST_TICKS;
        }
        if (ticks > MAX_REST_TICKS) {
            return MAX_REST_TICKS;
        }
        return ticks;
    }

    private static String errorJson(String message) {
        JsonObject object = new JsonObject();
        object.addProperty("success", false);
        object.addProperty("message", message);
        return object.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 120 ? value.substring(0, 120) + "..." : value;
    }
}
