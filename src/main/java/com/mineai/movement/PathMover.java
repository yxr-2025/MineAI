package com.mineai.movement;

import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A* based mover. Plans a path of stand positions and follows it node by node.
 * Falls back to a straight-line approach when no path is found.
 */
public final class PathMover implements NpcMover {

    private static final double DEFAULT_SPEED = 0.2D;
    private static final int MAX_NODES = 6000;
    private static final int MAX_DISTANCE = 64;
    private static final int MAX_STUCK_TICKS = 60;
    private static final int MAX_TOTAL_TICKS = 2400;

    private final NpcLocomotion locomotion = new NpcLocomotion();
    private final List<BlockPos> path = new ArrayList<>();

    private int index;
    private Vec3 target;
    private BlockPos goal;
    private double speed = DEFAULT_SPEED;
    private MoveState state = MoveState.IDLE;
    private int stuckTicks;
    private int totalTicks;
    private double lastDistance = Double.MAX_VALUE;

    @Override
    public void moveTo(AgentPlayer npc, Vec3 target, double speed) {
        this.target = target;
        this.speed = speed > 0.0D ? speed : DEFAULT_SPEED;
        this.state = MoveState.MOVING;
        this.index = 0;
        this.stuckTicks = 0;
        this.totalTicks = 0;
        this.lastDistance = Double.MAX_VALUE;
        this.path.clear();
        this.locomotion.reset();

        BlockPos start = npc.blockPosition();
        this.goal = AStarPathPlanner.resolveGoal(npc.level(), BlockPos.containing(target));
        if (this.goal == null) {
            state = MoveState.BLOCKED;
            return;
        }
        if (start.equals(this.goal)) {
            state = MoveState.ARRIVED;
            return;
        }

        List<BlockPos> planned = AStarPathPlanner.findPath(
                npc.level(), start, this.goal, MAX_NODES, MAX_DISTANCE);
        if (planned.isEmpty()) {
            path.add(this.goal);
        } else {
            path.addAll(planned);
        }
    }

    /**
     * Recomputes the path from the current position. Used after a door step,
     * because the NPC jumps past a node and the remaining path index no longer
     * matches the geometry.
     */
    private void replan(AgentPlayer npc) {
        path.clear();
        index = 0;
        stuckTicks = 0;
        lastDistance = Double.MAX_VALUE;

        if (goal == null) {
            state = MoveState.BLOCKED;
            return;
        }
        BlockPos start = npc.blockPosition();
        if (start.equals(goal)) {
            state = MoveState.ARRIVED;
            return;
        }
        List<BlockPos> planned = AStarPathPlanner.findPath(
                npc.level(), start, goal, MAX_NODES, MAX_DISTANCE);
        if (planned.isEmpty()) {
            path.add(goal);
        } else {
            path.addAll(planned);
        }
    }

    @Override
    public void stop() {
        target = null;
        goal = null;
        state = MoveState.IDLE;
        path.clear();
        index = 0;
        locomotion.reset();
    }

    @Override
    public void tick(AgentPlayer npc) {
        if (state != MoveState.MOVING) {
            locomotion.settle(npc);
            return;
        }
        if (++totalTicks > MAX_TOTAL_TICKS) {
            state = MoveState.TIMED_OUT;
            return;
        }

        int advanced = 0;
        while (index < path.size()) {
            BlockPos node = path.get(index);
            if (locomotion.hasArrived(npc, node.getX() + 0.5D, node.getZ() + 0.5D)) {
                index++;
                stuckTicks = 0;
                lastDistance = Double.MAX_VALUE;
                if (++advanced > 128) {
                    break;
                }
            } else {
                break;
            }
        }

        if (index >= path.size()) {
            state = MoveState.ARRIVED;
            return;
        }

        BlockPos node = path.get(index);
        double distance = locomotion.horizontalDistance(npc, node.getX() + 0.5D, node.getZ() + 0.5D);
        if (distance < lastDistance - 1.0E-4D) {
            stuckTicks = 0;
            lastDistance = distance;
        } else {
            stuckTicks++;
        }
        if (stuckTicks > MAX_STUCK_TICKS) {
            state = MoveState.BLOCKED;
            return;
        }

        StepResult result = locomotion.step(
                npc, node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D, speed);
        if (result == StepResult.BLOCKED) {
            state = MoveState.BLOCKED;
            return;
        }
        if (locomotion.consumeDoorStep()) {
            replan(npc);
        }
    }

    @Override
    public boolean isDone() {
        return state == MoveState.ARRIVED || state == MoveState.IDLE;
    }

    @Override
    public boolean isFailed() {
        return state == MoveState.BLOCKED || state == MoveState.TIMED_OUT;
    }

    @Override
    public MoveState state() {
        return state;
    }

    @Override
    public Vec3 target() {
        return target;
    }
}
