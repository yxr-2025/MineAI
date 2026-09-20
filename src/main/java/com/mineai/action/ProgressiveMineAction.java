package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import com.mineai.util.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Realistic mining: accumulates vanilla destroy progress per tick, broadcasts
 * the break animation, and breaks the block when progress reaches 1.
 */
public final class ProgressiveMineAction implements Action {

    private static final double MAX_REACH_SQR = 36.0D;
    private static final int MAX_TICKS = 1200;

    private final BlockPos target;
    private boolean started;
    private float progress;
    private int ticks;
    private int lastStage = -1;

    public ProgressiveMineAction(BlockPos target) {
        this.target = target.immutable();
    }

    @Override
    public void onStart(AgentPlayer npc) {
        started = true;

        BlockState state = npc.level().getBlockState(target);
        if (state.isAir()) {
            return;
        }
        if (npc.distanceToSqr(Vec3.atCenterOf(target)) > MAX_REACH_SQR) {
            return;
        }

        LookHelper.lookAt(npc, Vec3.atCenterOf(target));
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        if (!started) {
            return ActionResult.FAILED;
        }

        BlockState state = npc.level().getBlockState(target);
        if (state.isAir()) {
            return ActionResult.SUCCESS;
        }
        if (npc.distanceToSqr(Vec3.atCenterOf(target)) > MAX_REACH_SQR) {
            clearProgress(npc);
            return ActionResult.FAILED;
        }
        if (++ticks > MAX_TICKS) {
            clearProgress(npc);
            return ActionResult.FAILED;
        }

        LookHelper.lookAt(npc, Vec3.atCenterOf(target));

        float delta = state.getDestroyProgress(npc, npc.level(), target);
        if (delta <= 0.0F) {
            clearProgress(npc);
            return ActionResult.FAILED;
        }
        progress += delta;

        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != lastStage) {
            lastStage = stage;
            npc.level().destroyBlockProgress(npc.getId(), target, stage);
        }

        if (progress >= 1.0F) {
            clearProgress(npc);
            boolean broken = npc.gameMode.destroyBlock(target);
            return broken ? ActionResult.SUCCESS : ActionResult.FAILED;
        }
        return ActionResult.RUNNING;
    }

    @Override
    public void onComplete(AgentPlayer npc, ActionResult result) {
        clearProgress(npc);
    }

    @Override
    public void onCancel(AgentPlayer npc) {
        clearProgress(npc);
    }

    private void clearProgress(AgentPlayer npc) {
        if (lastStage >= 0) {
            npc.level().destroyBlockProgress(npc.getId(), target, -1);
            lastStage = -1;
        }
    }

    @Override
    public int timeoutTicks() {
        return MAX_TICKS + 100;
    }

    @Override
    public String describe() {
        return "ProgressiveMineAction(" + target.getX() + "," + target.getY() + "," + target.getZ() + ")";
    }
}
