package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import com.mineai.util.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Instant block break for stage 0-1.
 *
 * <p>Triggers the vanilla destroy path (drops, tool durability, events) but
 * does not simulate dig progress. A future {@code ProgressiveMineAction} will
 * cover realistic timing without changing this class.</p>
 */
public final class MineAction implements Action {

    private static final double MAX_REACH_SQR = 36.0D;

    private final BlockPos target;
    private boolean executed;
    private ActionResult result = ActionResult.FAILED;

    public MineAction(BlockPos target) {
        this.target = target.immutable();
    }

    @Override
    public void onStart(AgentPlayer npc) {
        result = mine(npc);
        executed = true;
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        return executed ? result : ActionResult.FAILED;
    }

    private ActionResult mine(AgentPlayer npc) {
        BlockState state = npc.level().getBlockState(target);
        if (state.isAir()) {
            return ActionResult.FAILED;
        }
        Vec3 center = Vec3.atCenterOf(target);
        if (npc.distanceToSqr(center) > MAX_REACH_SQR) {
            return ActionResult.FAILED;
        }
        LookHelper.lookAt(npc, center);
        boolean broken = npc.gameMode.destroyBlock(target);
        return broken ? ActionResult.SUCCESS : ActionResult.FAILED;
    }

    @Override
    public String describe() {
        return "MineAction(" + target.getX() + "," + target.getY() + "," + target.getZ() + ")";
    }
}
