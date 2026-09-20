package com.mineai.movement;

import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared per-tick movement primitives for server-driven NPCs.
 *
 * <p>Handles walking, one-block steps, wall sliding, falling, swimming, ladder
 * climbing and opening doors. A {@code ServerPlayer} is not gravity-simulated on
 * the server, so vertical motion is applied here.</p>
 */
public final class NpcLocomotion {

    public static final double ARRIVE_EPSILON = 0.35D;
    private static final double GRAVITY = 0.08D;
    private static final double MAX_FALL_SPEED = 1.0D;
    private static final double JUMP_IMPULSE = 0.42D;
    private static final double CLIMB_SPEED = 0.2D;
    private static final double SWIM_UP_SPEED = 0.08D;
    private static final double WATER_SINK_SPEED = 0.02D;

    private boolean airborne;
    private double fallSpeed;
    private boolean doorStepped;

    public void reset() {
        airborne = false;
        fallSpeed = 0.0D;
        doorStepped = false;
    }

    /**
     * True once if the previous step teleported the NPC through a doorway.
     * Movers use this to reset their stuck tracking.
     */
    public boolean consumeDoorStep() {
        boolean value = doorStepped;
        doorStepped = false;
        return value;
    }

    public boolean hasArrived(AgentPlayer npc, double targetX, double targetZ) {
        return horizontalDistance(npc, targetX, targetZ) <= ARRIVE_EPSILON;
    }

    public double horizontalDistance(AgentPlayer npc, double targetX, double targetZ) {
        double dx = targetX - npc.getX();
        double dz = targetZ - npc.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public StepResult step(AgentPlayer npc, double targetX, double targetY, double targetZ, double speed) {
        double dx = targetX - npc.getX();
        double dz = targetZ - npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        BlockPos feet = npc.blockPosition();
        boolean inWater = NpcTraversal.isWater(npc.level(), feet)
                || NpcTraversal.isWater(npc.level(), feet.above());
        boolean onLadder = NpcTraversal.isLadder(npc.level(), feet);

        if (horizontal <= ARRIVE_EPSILON) {
            if (onLadder || inWater) {
                verticalMotion(npc, targetY, onLadder, inWater, false);
            }
            return StepResult.ARRIVED;
        }

        double step = Math.min(speed, horizontal);
        double mx = dx / horizontal * step;
        double mz = dz / horizontal * step;
        boolean jumpRequested = false;

        boolean feetBlocked = blockedAhead(npc, mx, mz, 0);
        boolean headBlocked = blockedAhead(npc, mx, mz, 1);

        if (feetBlocked || headBlocked) {
            if (tryOpenDoor(npc, mx, mz)) {
                return StepResult.MOVED;
            }
            if (feetBlocked && !headBlocked) {
                jumpRequested = true;
            } else {
                boolean xClear = !blockedAhead(npc, mx, 0.0D, 0) && !blockedAhead(npc, mx, 0.0D, 1);
                boolean zClear = !blockedAhead(npc, 0.0D, mz, 0) && !blockedAhead(npc, 0.0D, mz, 1);
                if (xClear && !zClear) {
                    mz = 0.0D;
                } else if (zClear && !xClear) {
                    mx = 0.0D;
                } else if (!xClear && !zClear) {
                    return StepResult.BLOCKED;
                } else if (Math.abs(mx) >= Math.abs(mz)) {
                    mz = 0.0D;
                } else {
                    mx = 0.0D;
                }
            }
        }

        double my = verticalMotion(npc, targetY, onLadder, inWater, jumpRequested);
        npc.move(MoverType.SELF, new Vec3(mx, my, mz));

        if (airborne && fallSpeed > 0.0D && standingOnGround(npc)) {
            airborne = false;
            fallSpeed = 0.0D;
        }

        face(npc, dx, dz);
        return StepResult.MOVED;
    }

    /**
     * Passive gravity applied every tick, even when no move action is running.
     */
    public void settle(AgentPlayer npc) {
        BlockPos feet = npc.blockPosition();
        if (NpcTraversal.isWater(npc.level(), feet) || NpcTraversal.isWater(npc.level(), feet.above())) {
            npc.move(MoverType.SELF, new Vec3(0.0D, -WATER_SINK_SPEED, 0.0D));
            airborne = false;
            fallSpeed = 0.0D;
            return;
        }
        if (standingOnGround(npc)) {
            airborne = false;
            fallSpeed = 0.0D;
            return;
        }
        airborne = true;
        fallSpeed = Math.min(fallSpeed + GRAVITY, MAX_FALL_SPEED);
        npc.move(MoverType.SELF, new Vec3(0.0D, -fallSpeed, 0.0D));
        if (fallSpeed > 0.0D && standingOnGround(npc)) {
            airborne = false;
            fallSpeed = 0.0D;
        }
    }

    public void face(AgentPlayer npc, double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setXRot(0.0F);
    }

    private double verticalMotion(AgentPlayer npc, double targetY, boolean onLadder,
                                  boolean inWater, boolean jumpRequested) {
        if (onLadder) {
            double diff = targetY - npc.getY();
            if (diff > 0.1D) {
                fallSpeed = 0.0D;
                return CLIMB_SPEED;
            }
            if (diff < -0.1D) {
                fallSpeed = 0.0D;
                return -CLIMB_SPEED;
            }
            fallSpeed = 0.0D;
            return 0.0D;
        }

        if (inWater) {
            airborne = false;
            fallSpeed = 0.0D;
            return targetY > npc.getY() + 0.1D ? SWIM_UP_SPEED : -WATER_SINK_SPEED;
        }

        if (jumpRequested) {
            airborne = true;
            fallSpeed = -JUMP_IMPULSE;
            return JUMP_IMPULSE;
        }

        if (!airborne) {
            if (standingOnGround(npc)) {
                fallSpeed = 0.0D;
                return 0.0D;
            }
            airborne = true;
            fallSpeed = 0.0D;
        }

        fallSpeed = Math.min(fallSpeed + GRAVITY, MAX_FALL_SPEED);
        return -fallSpeed;
    }

    /**
     * True when the entity bounding box, shifted by the intended movement,
     * overlaps a non-clear block at {@code feetY + yOffset}.
     */
    private static boolean blockedAhead(AgentPlayer npc, double mx, double mz, int yOffset) {
        AABB box = npc.getBoundingBox().move(mx, 0.0D, mz);
        int y = Mth.floor(npc.getY()) + yOffset;
        int minX = Mth.floor(box.minX + 1.0E-6D);
        int maxX = Mth.floor(box.maxX - 1.0E-6D);
        int minZ = Mth.floor(box.minZ + 1.0E-6D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-6D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!NpcCollision.isClear(npc.level(), new BlockPos(x, y, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryOpenDoor(AgentPlayer npc, double mx, double mz) {
        BlockPos pos = doorAhead(npc, mx, mz);
        if (pos == null) {
            return false;
        }
        BlockState state = npc.level().getBlockState(pos);
        if (!NpcTraversal.isDoorLike(state)) {
            return false;
        }

        if (!NpcTraversal.isDoorOpen(state)) {
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(npc, npc.level(), state, pos, true);
            } else if (state.hasProperty(FenceGateBlock.OPEN)) {
                npc.level().setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
            }
        }

        // An open door block still contributes collision, so a 0.6-wide body
        // cannot squeeze through by walking. Once adjacent, step across.
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        if (Math.abs(npc.getX() - centerX) > 1.0D || Math.abs(npc.getZ() - centerZ) > 1.0D) {
            return true;
        }

        double dirX = Math.abs(mx) < 1.0E-4D ? 0.0D : Math.signum(mx);
        double dirZ = Math.abs(mz) < 1.0E-4D ? 0.0D : Math.signum(mz);
        double landingX = centerX + dirX * 0.9D;
        double landingZ = centerZ + dirZ * 0.9D;
        BlockPos landing = BlockPos.containing(landingX, npc.getY(), landingZ);
        if (NpcCollision.isClear(npc.level(), landing)
                && NpcCollision.isClear(npc.level(), landing.above())) {
            npc.setPos(landingX, npc.getY(), landingZ);
            doorStepped = true;
        }
        return true;
    }

    private static BlockPos doorAhead(AgentPlayer npc, double mx, double mz) {
        AABB box = npc.getBoundingBox().move(mx, 0.0D, mz);
        int baseY = Mth.floor(npc.getY());
        int minX = Mth.floor(box.minX + 1.0E-6D);
        int maxX = Mth.floor(box.maxX - 1.0E-6D);
        int minZ = Mth.floor(box.minZ + 1.0E-6D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-6D);
        for (int y = baseY; y <= baseY + 1; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (NpcTraversal.isDoorLike(npc.level().getBlockState(pos))) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Grounded means there is a solid block below AND the feet are actually at
     * its top surface.
     */
    private static boolean standingOnGround(AgentPlayer npc) {
        if (npc.onGround()) {
            return true;
        }
        BlockPos below = npc.blockPosition().below();
        if (!NpcCollision.isSolid(npc.level(), below)) {
            return false;
        }
        double top = below.getY() + 1.0D;
        return Math.abs(npc.getY() - top) < 0.08D;
    }
}
