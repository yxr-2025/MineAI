package com.mineai.action;

import com.mineai.entity.AgentPlayer;

/**
 * A single unit of work executed by an {@link ActionQueue}.
 *
 * <p>{@link #onStart(AgentPlayer)} runs exactly once. {@link #tick(AgentPlayer)}
 * then runs once per server tick until it returns something other than
 * {@link ActionResult#RUNNING}.</p>
 */
public interface Action {

    default void onStart(AgentPlayer npc) {
    }

    ActionResult tick(AgentPlayer npc);

    default void onComplete(AgentPlayer npc, ActionResult result) {
    }

    default void onCancel(AgentPlayer npc) {
    }

    default String describe() {
        return getClass().getSimpleName();
    }

    /**
     * Per-action timeout in ticks. Values &lt;= 0 fall back to the queue default.
     */
    default int timeoutTicks() {
        return -1;
    }
}
