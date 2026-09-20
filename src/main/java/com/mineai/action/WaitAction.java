package com.mineai.action;

import com.mineai.entity.AgentPlayer;

/**
 * Idles for a fixed number of ticks. Used by processes that must wait for a
 * world mechanism (furnace smelting, crop growth) instead of polling the model.
 */
public final class WaitAction implements Action {

    private final int ticks;
    private int elapsed;

    public WaitAction(int ticks) {
        this.ticks = Math.max(1, ticks);
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        return ++elapsed >= ticks ? ActionResult.SUCCESS : ActionResult.RUNNING;
    }

    @Override
    public int timeoutTicks() {
        return ticks + 60;
    }

    @Override
    public String describe() {
        return "WaitAction(" + ticks + "t)";
    }
}
