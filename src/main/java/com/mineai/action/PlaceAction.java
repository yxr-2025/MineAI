package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import com.mineai.util.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places the main-hand block at {@code target}.
 *
 * <p>The constructor argument is the final block position. The support block
 * and click face are derived internally, because a {@link BlockHitResult}
 * points at the block being clicked, not the block being placed.</p>
 */
public final class PlaceAction implements Action {

    private static final double MAX_REACH_SQR = 36.0D;
    private static final Direction[] FACE_ORDER = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    private final BlockPos target;
    private boolean executed;
    private ActionResult result = ActionResult.FAILED;

    public PlaceAction(BlockPos target) {
        this.target = target.immutable();
    }

    @Override
    public void onStart(AgentPlayer npc) {
        result = place(npc);
        executed = true;
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        return executed ? result : ActionResult.FAILED;
    }

    private ActionResult place(AgentPlayer npc) {
        BlockState current = npc.level().getBlockState(target);
        if (!current.isAir() && !current.canBeReplaced()) {
            return ActionResult.FAILED;
        }

        ItemStack stack = npc.getMainHandItem();
        if (stack.isEmpty()) {
            return ActionResult.FAILED;
        }

        for (Direction face : FACE_ORDER) {
            BlockPos support = target.relative(face.getOpposite());
            BlockState supportState = npc.level().getBlockState(support);
            if (supportState.isAir()) {
                continue;
            }
            if (!supportState.isFaceSturdy(npc.level(), support, face)) {
                continue;
            }

            Vec3 hitVec = Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.5D,
                    face.getStepY() * 0.5D,
                    face.getStepZ() * 0.5D);
            if (npc.distanceToSqr(hitVec) > MAX_REACH_SQR) {
                continue;
            }

            LookHelper.lookAt(npc, hitVec);
            BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
            InteractionResult interaction = npc.gameMode.useItemOn(
                    npc, npc.level(), stack, InteractionHand.MAIN_HAND, hit);
            if (interaction.consumesAction()) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.FAILED;
    }

    @Override
    public String describe() {
        return "PlaceAction(" + target.getX() + "," + target.getY() + "," + target.getZ() + ")";
    }
}
