package com.mineai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision helpers for {@link NpcMover}. All checks are read-only.
 */
public final class NpcCollision {

    private NpcCollision() {
    }

    /**
     * True when both the feet and head space at {@code feetPos} are free.
     */
    public static boolean isPassable(BlockGetter level, BlockPos feetPos) {
        return isClear(level, feetPos) && isClear(level, feetPos.above());
    }

    public static boolean isClear(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty();
    }

    public static boolean isSolid(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    public static boolean hasHeadRoom(BlockGetter level, BlockPos feetPos) {
        return isClear(level, feetPos.above());
    }
}
