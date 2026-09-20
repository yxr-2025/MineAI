package com.mineai.action;

import com.mineai.MineAiMod;
import com.mineai.entity.AgentPlayer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serial action executor bound to a single {@link AgentPlayer}.
 */
public final class ActionQueue {

    public static final int DEFAULT_TIMEOUT_TICKS = 400;

    private final Deque<Action> pending = new ArrayDeque<>();
    private Action current;
    private int currentTicks;
    private int currentTimeout = DEFAULT_TIMEOUT_TICKS;
    private int timeoutTicks = DEFAULT_TIMEOUT_TICKS;
    private ActionResult lastResult;

    public void enqueue(Action action) {
        pending.addLast(action);
    }

    public void clear(AgentPlayer npc) {
        if (current != null) {
            current.onCancel(npc);
        }
        pending.clear();
        current = null;
        currentTicks = 0;
    }

    public boolean isBusy() {
        return current != null || !pending.isEmpty();
    }

    public Action currentAction() {
        return current;
    }

    public ActionResult lastResult() {
        return lastResult;
    }

    public int pendingCount() {
        return pending.size();
    }

    public void setTimeoutTicks(int ticks) {
        this.timeoutTicks = Math.max(1, ticks);
    }

    public void tick(AgentPlayer npc) {
        if (current == null) {
            current = pending.pollFirst();
            if (current == null) {
                return;
            }
            currentTicks = 0;
            currentTimeout = current.timeoutTicks() > 0 ? current.timeoutTicks() : timeoutTicks;
            current.onStart(npc);
        }

        currentTicks++;
        if (currentTicks > currentTimeout) {
            finish(npc, ActionResult.FAILED);
            return;
        }

        ActionResult result = current.tick(npc);
        if (result == null) {
            result = ActionResult.FAILED;
        }
        if (result != ActionResult.RUNNING) {
            finish(npc, result);
        }
    }

    private void finish(AgentPlayer npc, ActionResult result) {
        Action finished = current;
        current = null;
        currentTicks = 0;
        lastResult = result;
        if (finished == null) {
            return;
        }
        MineAiMod.LOGGER.info("[mineai] action {} finished with {}", finished.describe(), result);
        if (result == ActionResult.CANCELLED) {
            finished.onCancel(npc);
        } else {
            finished.onComplete(npc, result);
        }
    }
}
