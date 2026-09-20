package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import net.minecraft.world.phys.Vec3;

public final class MoveAction implements Action {

    private final Vec3 target;
    private final double speed;
    private boolean started;

    public MoveAction(Vec3 target, double speed) {
        this.target = target;
        this.speed = speed;
    }

    public MoveAction(Vec3 target) {
        this(target, 0.2D);
    }

    @Override
    public void onStart(AgentPlayer npc) {
        npc.mover().moveTo(npc, target, speed);
        started = true;
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        if (!started) {
            return ActionResult.FAILED;
        }
        return switch (npc.mover().state()) {
            case MOVING -> ActionResult.RUNNING;
            case ARRIVED, IDLE -> ActionResult.SUCCESS;
            case BLOCKED, TIMED_OUT -> ActionResult.FAILED;
        };
    }

    @Override
    public void onComplete(AgentPlayer npc, ActionResult result) {
        npc.mover().stop();
    }

    @Override
    public int timeoutTicks() {
        return 2500;
    }

    @Override
    public String describe() {
        return "MoveAction(" + target.x + "," + target.y + "," + target.z + ")";
    }
}
