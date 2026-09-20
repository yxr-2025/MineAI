package com.mineai.agent;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Mutable state of one deterministic program execution. Both recorded skills
 * and generated procedures run through this.
 *
 * <p>The controller advances it once per server tick; no model call happens
 * between steps.</p>
 */
public final class SkillRun {

    private final String name;
    private final BlockPos origin;
    private final BlockPos anchor;
    private final List<JsonObject> steps;
    private int index;
    private boolean awaitingAction;
    private int ticks;

    public SkillRun(String name, List<JsonObject> steps, BlockPos origin, BlockPos anchor) {
        this.name = name;
        this.steps = steps;
        this.origin = origin;
        this.anchor = anchor;
    }

    public String name() {
        return name;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public int index() {
        return index;
    }

    public int size() {
        return steps.size();
    }

    public boolean hasNext() {
        return index < steps.size();
    }

    public JsonObject nextStep() {
        return steps.get(index++);
    }

    public boolean isAwaitingAction() {
        return awaitingAction;
    }

    public void setAwaitingAction(boolean value) {
        awaitingAction = value;
    }

    public int tick() {
        return ++ticks;
    }

    /**
     * Translates a recorded coordinate from the recording origin to the anchor.
     */
    public int shiftX(JsonObject args) {
        return (args.has("x") ? args.get("x").getAsInt() : 0) + (anchor.getX() - origin.getX());
    }

    public int shiftY(JsonObject args) {
        return (args.has("y") ? args.get("y").getAsInt() : 0) + (anchor.getY() - origin.getY());
    }

    public int shiftZ(JsonObject args) {
        return (args.has("z") ? args.get("z").getAsInt() : 0) + (anchor.getZ() - origin.getZ());
    }
}
