package com.mineai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded A* over walkable stand positions.
 *
 * <p>Supports flat walking, diagonals without corner cutting, one-block step
 * ups, and short drops. Search is capped by node count and distance so it can
 * run inside a server tick.</p>
 */
public final class AStarPathPlanner {

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private AStarPathPlanner() {
    }

    public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos goal,
                                          int maxNodes, int maxDistance) {
        if (start.equals(goal)) {
            return List.of();
        }
        double dx = start.getX() - goal.getX();
        double dz = start.getZ() - goal.getZ();
        if (dx * dx + dz * dz > (double) maxDistance * maxDistance) {
            return List.of();
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));
        Map<BlockPos, Node> best = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, null, 0.0D, heuristic(start, goal));
        best.put(start, startNode);
        open.add(startNode);

        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node current = open.poll();
            if (!closed.add(current.pos)) {
                continue;
            }
            expanded++;

            if (current.pos.equals(goal)) {
                return reconstruct(current);
            }

            for (Neighbor neighbor : neighbors(level, current.pos)) {
                if (closed.contains(neighbor.pos)) {
                    continue;
                }
                double g = current.g + neighbor.cost;
                Node existing = best.get(neighbor.pos);
                if (existing != null && g >= existing.g) {
                    continue;
                }
                Node node = new Node(neighbor.pos, current, g, heuristic(neighbor.pos, goal));
                best.put(neighbor.pos, node);
                open.add(node);
            }
        }
        return List.of();
    }

    public static boolean canStand(Level level, BlockPos pos) {
        if (NpcTraversal.isLava(level, pos) || NpcTraversal.isLava(level, pos.above())) {
            return false;
        }
        if (NpcTraversal.isWater(level, pos)) {
            // swim node: the body space must not be solid
            return NpcCollision.isClear(level, pos) && NpcCollision.isClear(level, pos.above());
        }
        if (!passable(level, pos) || !passable(level, pos.above())) {
            return false;
        }
        if (NpcTraversal.isLadder(level, pos) || NpcTraversal.isLadder(level, pos.above())) {
            return true;
        }
        return NpcCollision.isSolid(level, pos.below());
    }

    /**
     * A closed door is treated as passable because the mover opens it on
     * contact.
     */
    private static boolean passable(Level level, BlockPos pos) {
        if (NpcTraversal.isDoorLike(level.getBlockState(pos))) {
            return true;
        }
        return NpcCollision.isClear(level, pos);
    }

    /**
     * Finds a standable position near the given hint (used when the model gives
     * a block coordinate that is not itself a valid standing spot).
     */
    public static BlockPos resolveGoal(Level level, BlockPos hint) {
        if (canStand(level, hint)) {
            return hint;
        }
        if (canStand(level, hint.above())) {
            return hint.above();
        }
        if (canStand(level, hint.below())) {
            return hint.below();
        }
        for (int dy = 1; dy <= 4; dy++) {
            if (canStand(level, hint.above(dy))) {
                return hint.above(dy);
            }
            if (canStand(level, hint.below(dy))) {
                return hint.below(dy);
            }
        }
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                for (int ry = -2; ry <= 2; ry++) {
                    BlockPos candidate = hint.offset(rx, ry, rz);
                    if (canStand(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static List<Neighbor> neighbors(Level level, BlockPos pos) {
        List<Neighbor> result = new ArrayList<>(8);
        boolean canMoveVertically = NpcTraversal.isLadder(level, pos)
                || NpcTraversal.isLadder(level, pos.above())
                || NpcTraversal.isWater(level, pos);

        for (int[] d : HORIZONTAL) {
            BlockPos flat = pos.offset(d[0], 0, d[1]);
            if (canStand(level, flat)) {
                result.add(new Neighbor(flat, cost(level, flat)));
                continue;
            }
            BlockPos up = flat.above();
            if (canStand(level, up)) {
                result.add(new Neighbor(up, cost(level, up) + 0.4D));
            }
        }

        for (int[] d : DIAGONAL) {
            BlockPos flat = pos.offset(d[0], 0, d[1]);
            if (!canStand(level, flat)) {
                continue;
            }
            if (!canStand(level, pos.offset(d[0], 0, 0))) {
                continue;
            }
            if (!canStand(level, pos.offset(0, 0, d[1]))) {
                continue;
            }
            result.add(new Neighbor(flat, cost(level, flat) + 0.41421D));
        }

        BlockPos up = pos.above();
        if (canStand(level, up)) {
            result.add(new Neighbor(up, cost(level, up) + (canMoveVertically ? 0.3D : 0.5D)));
        }

        for (int drop = 1; drop <= 3; drop++) {
            BlockPos down = pos.below(drop);
            if (canStand(level, down)) {
                result.add(new Neighbor(down, cost(level, down) + drop * 0.5D));
                break;
            }
            if (!canMoveVertically && NpcCollision.isSolid(level, down)) {
                break;
            }
        }

        return result;
    }

    private static double cost(Level level, BlockPos pos) {
        return NpcTraversal.isWater(level, pos) ? 1.6D : 1.0D;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        double dx = Math.abs(from.getX() - to.getX());
        double dz = Math.abs(from.getZ() - to.getZ());
        double dy = Math.abs(from.getY() - to.getY());
        double octile = Math.max(dx, dz) + (Math.sqrt(2.0D) - 1.0D) * Math.min(dx, dz);
        return octile + dy * 1.5D;
    }

    private static List<BlockPos> reconstruct(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node current = node;
        while (current.parent != null) {
            path.add(current.pos);
            current = current.parent;
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private record Neighbor(BlockPos pos, double cost) {
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = g + h;
        }
    }
}
