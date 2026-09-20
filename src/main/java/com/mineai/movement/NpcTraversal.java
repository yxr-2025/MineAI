package com.mineai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Traversal classification: water, ladders and openable doors.
 */
public final class NpcTraversal {

    private NpcTraversal() {
    }

    public static boolean isWater(BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.WATER);
    }

    public static boolean isLava(BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.LAVA);
    }

    public static boolean isLadder(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LadderBlock
                || state.getBlock() instanceof ScaffoldingBlock
                || state.is(BlockTags.CLIMBABLE);
    }

    public static boolean isDoorLike(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    public static boolean isDoorOpen(BlockState state) {
        if (state.hasProperty(DoorBlock.OPEN)) {
            return state.getValue(DoorBlock.OPEN);
        }
        if (state.hasProperty(FenceGateBlock.OPEN)) {
            return state.getValue(FenceGateBlock.OPEN);
        }
        return false;
    }
}
