package com.mineai.command;

import com.mineai.MineAiMod;
import com.mineai.agent.AgentController;
import com.mineai.agent.AgentMemory;
import com.mineai.agent.Objective;
import com.mineai.agent.SharedBlackboard;
import com.mineai.agent.Skill;
import com.mineai.config.LlmConfig;
import com.mineai.entity.AgentPlayer;
import com.mineai.entity.AgentPlayerManager;
import com.mineai.llm.LlmService;
import com.mineai.state.WorldStateCollector;
import com.mineai.util.RegistryIds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class NPCCommand {

    private NPCCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(context -> spawn(context.getSource())))
                .then(Commands.literal("remove")
                        .executes(context -> removeAll(context.getSource()))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> remove(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("board")
                        .executes(context -> board(context.getSource())))
                .then(Commands.literal("test")
                        .executes(context -> test(context.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> test(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("state")
                        .executes(context -> state(context.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> state(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("food")
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 20))
                                        .executes(context -> debugFood(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "level")))))
                        .then(Commands.literal("health")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D, 1024.0D))
                                        .executes(context -> debugHealth(context.getSource(),
                                                DoubleArgumentType.getDouble(context, "amount")))))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> debugGoto(context.getSource(),
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"))))))))
                .then(Commands.literal("agent")
                        .then(Commands.literal("select")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> agentSelect(context.getSource(),
                                                StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("start")
                                .then(Commands.argument("goal", StringArgumentType.greedyString())
                                        .executes(context -> agentStart(context.getSource(),
                                                StringArgumentType.getString(context, "goal")))))
                        .then(Commands.literal("obtain")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                                .then(Commands.argument("goal", StringArgumentType.greedyString())
                                                        .executes(context -> agentObtain(context.getSource(),
                                                                ResourceLocationArgument.getId(context, "item").toString(),
                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                StringArgumentType.getString(context, "goal")))))))
                        .then(Commands.literal("mission")
                                .then(Commands.argument("mission", StringArgumentType.greedyString())
                                        .executes(context -> agentMission(context.getSource(),
                                                StringArgumentType.getString(context, "mission")))))
                        .then(Commands.literal("live")
                                .executes(context -> agentLive(context.getSource())))
                        .then(Commands.literal("memory")
                                .executes(context -> agentMemory(context.getSource())))
                        .then(Commands.literal("forget")
                                .executes(context -> agentForget(context.getSource())))
                        .then(Commands.literal("stop")
                                .executes(context -> agentStop(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> agentStatus(context.getSource()))))
        );
    }

    private static int spawn(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        AgentPlayer npc;
        try {
            npc = AgentPlayerManager.spawn(server, level, pos);
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Failed to spawn NPC: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Spawned NPC " + npc.npcId() + " (" + npc.getName().getString() + ") at "
                        + pos.x + ", " + pos.y + ", " + pos.z), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String npcId) {
        if (AgentPlayerManager.remove(source.getServer(), npcId)) {
            source.sendSuccess(() -> Component.literal("Removed NPC " + npcId), true);
            return 1;
        }
        source.sendFailure(Component.literal("No NPC with id " + npcId));
        return 0;
    }

    private static int removeAll(CommandSourceStack source) {
        int count = AgentPlayerManager.removeAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed " + count + " NPC(s)"), true);
        return count;
    }

    private static int list(CommandSourceStack source) {
        var npcs = AgentPlayerManager.all();
        if (npcs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active NPCs"), false);
            return 0;
        }
        for (AgentPlayer npc : npcs) {
            source.sendSuccess(() -> Component.literal(
                    npc.npcId()
                            + " | " + npc.getName().getString()
                            + " | " + npc.getUUID()
                            + " | " + npc.level().dimension().location()
                            + " | " + format(npc.getX()) + "," + format(npc.getY()) + "," + format(npc.getZ())
                            + " | move=" + npc.mover().state()
                            + " | action=" + (npc.actionQueue().currentAction() == null
                                    ? "none"
                                    : npc.actionQueue().currentAction().describe())
                            + " | queued=" + npc.actionQueue().pendingCount()), false);
        }
        return npcs.size();
    }

    private static int test(CommandSourceStack source, String npcId) {
        AgentPlayer npc = npcId == null
                ? AgentPlayerManager.selected().orElse(null)
                : AgentPlayerManager.get(npcId).orElse(null);

        if (npc == null) {
            source.sendFailure(Component.literal(npcId == null
                    ? "No active NPC"
                    : "No NPC with id " + npcId));
            return 0;
        }

        NpcTestScript.run(npc);
        source.sendSuccess(() -> Component.literal("Queued test script for NPC " + npc.npcId()), true);
        return 1;
    }

    private static int state(CommandSourceStack source, String npcId) {
        AgentPlayer npc = npcId == null
                ? AgentPlayerManager.selected().orElse(null)
                : AgentPlayerManager.get(npcId).orElse(null);

        if (npc == null) {
            source.sendFailure(Component.literal(npcId == null
                    ? "No active NPC"
                    : "No NPC with id " + npcId));
            return 0;
        }

        String snapshot = WorldStateCollector.collect(npc).toString();
        MineAiMod.LOGGER.info("NPC state {}: {}", npc.npcId(), snapshot);
        source.sendSuccess(() -> Component.literal(snapshot), false);
        return 1;
    }

    private static int board(CommandSourceStack source) {
        var entries = SharedBlackboard.all();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Blackboard is empty"), false);
            return 0;
        }
        for (SharedBlackboard.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal(
                    entry.key() + " = " + entry.value() + " (by " + entry.author() + ")"), false);
        }
        return entries.size();
    }

    private static int debugFood(CommandSourceStack source, int level) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        npc.getFoodData().setFoodLevel(level);
        npc.getFoodData().setSaturation(0.0F);
        source.sendSuccess(() -> Component.literal("food set to " + level), true);
        return 1;
    }

    private static int debugHealth(CommandSourceStack source, double amount) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        npc.setHealth((float) amount);
        source.sendSuccess(() -> Component.literal("health set to " + amount), true);
        return 1;
    }

    private static int debugGoto(CommandSourceStack source, double x, double y, double z) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        npc.actionQueue().enqueue(new com.mineai.action.MoveAction(
                new net.minecraft.world.phys.Vec3(x, y, z)));
        source.sendSuccess(() -> Component.literal("queued goto " + x + "," + y + "," + z), true);
        return 1;
    }

    private static int agentSelect(CommandSourceStack source, String npcId) {
        if (AgentPlayerManager.get(npcId).isEmpty()) {
            source.sendFailure(Component.literal("No NPC with id " + npcId));
            return 0;
        }
        AgentPlayerManager.select(npcId);
        source.sendSuccess(() -> Component.literal("Agent commands now target " + npcId), true);
        return 1;
    }

    private static int agentStart(CommandSourceStack source, String goal) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        if (!LlmService.isReady()) {
            source.sendFailure(Component.literal(
                    "LLM provider is not configured. Edit config/mineai/llm.json or set "
                            + LlmConfig.ENV_API_KEY));
            return 0;
        }

        LlmConfig config = LlmService.config();
        int maxSteps = config == null ? 12 : config.maxSteps();
        npc.agentController().start(goal, maxSteps);
        source.sendSuccess(() -> Component.literal(
                "Agent started for " + npc.npcId() + " with goal: " + goal), true);
        return 1;
    }

    private static int agentMission(CommandSourceStack source, String mission) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        if (!LlmService.isReady()) {
            source.sendFailure(Component.literal(
                    "LLM provider is not configured. Edit config/mineai/llm.json or set "
                            + LlmConfig.ENV_API_KEY));
            return 0;
        }

        LlmConfig config = LlmService.config();
        int maxSteps = config == null ? 12 : config.maxSteps();
        int maxGoals = config == null ? 8 : config.maxGoals();
        boolean reflection = config == null || config.reflectionEnabled();
        npc.agentController().configureReflection(
                config == null ? 1 : config.reflectionMinFailures(),
                config == null ? 3 : config.reflectionSuccessInterval());
        npc.agentController().startMission(mission, maxSteps, maxGoals, reflection);
        source.sendSuccess(() -> Component.literal(
                "Autonomous mission started for " + npc.npcId() + ": " + mission), true);
        return 1;
    }

    private static int agentObtain(CommandSourceStack source, String item, int count, String goal) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        if (!LlmService.isReady()) {
            source.sendFailure(Component.literal(
                    "LLM provider is not configured. Edit config/mineai/llm.json or set "
                            + LlmConfig.ENV_API_KEY));
            return 0;
        }
        if (RegistryIds.itemById(item) == null) {
            source.sendFailure(Component.literal("Unknown item: " + item));
            return 0;
        }

        LlmConfig config = LlmService.config();
        int maxSteps = config == null ? 12 : config.maxSteps();
        npc.agentController().configureReflection(
                config == null ? 1 : config.reflectionMinFailures(),
                config == null ? 3 : config.reflectionSuccessInterval());
        int maxGoals = config == null ? 8 : config.maxGoals();
        npc.agentController().startMission(goal, maxSteps, maxGoals, true, Objective.haveItem(item, count));
        source.sendSuccess(() -> Component.literal(
                "Objective mission started: " + count + "x " + item), true);
        return 1;
    }

    private static int agentLive(CommandSourceStack source) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        if (!LlmService.isReady()) {
            source.sendFailure(Component.literal(
                    "LLM provider is not configured. Edit config/mineai/llm.json or set "
                            + LlmConfig.ENV_API_KEY));
            return 0;
        }

        LlmConfig config = LlmService.config();
        int maxSteps = config == null ? 12 : config.maxSteps();
        int maxGoals = config == null ? 8 : config.maxGoals();
        boolean reflection = config == null || config.reflectionEnabled();
        npc.agentController().configureReflection(
                config == null ? 1 : config.reflectionMinFailures(),
                config == null ? 3 : config.reflectionSuccessInterval());
        npc.agentController().startLifeLoop(maxSteps, maxGoals, reflection);
        source.sendSuccess(() -> Component.literal(
                "Life loop started for " + npc.npcId() + " (self-directed, no mission needed)"), true);
        return 1;
    }

    private static int agentMemory(CommandSourceStack source) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        AgentMemory memory = npc.agentController().memory();
        source.sendSuccess(() -> Component.literal("notes=" + memory.notes().size()
                + " skills=" + memory.skills().size()
                + " history=" + memory.history().size()), false);
        for (String note : memory.notes()) {
            source.sendSuccess(() -> Component.literal("note: " + note), false);
        }
        for (Skill skill : memory.skills()) {
            source.sendSuccess(() -> Component.literal("skill: " + skill.summary()), false);
        }
        return 1;
    }

    private static int agentForget(CommandSourceStack source) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        npc.agentController().memory().clear();
        source.sendSuccess(() -> Component.literal("Cleared memory for " + npc.npcId()), true);
        return 1;
    }

    private static int agentStop(CommandSourceStack source) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        if (npc.agentControllerOrNull() != null) {
            npc.agentControllerOrNull().stop();
        }
        source.sendSuccess(() -> Component.literal("Agent stopped for " + npc.npcId()), true);
        return 1;
    }

    private static int agentStatus(CommandSourceStack source) {
        AgentPlayer npc = AgentPlayerManager.selected().orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal("No active NPC"));
            return 0;
        }
        AgentController controller = npc.agentControllerOrNull();
        String status = controller == null
                ? "agent=not-started"
                : "agent=" + controller.state()
                        + (controller.isLifeLoop() ? " mode=life" : "")
                        + " step=" + controller.step()
                        + " plan=" + controller.planDone() + "/" + controller.planSize()
                        + (controller.restSeconds() > 0 ? " rest=" + controller.restSeconds() + "s" : "")
                        + " goal=" + controller.goal()
                        + " last=" + controller.lastMessage()
                        + " error=" + controller.error();
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

