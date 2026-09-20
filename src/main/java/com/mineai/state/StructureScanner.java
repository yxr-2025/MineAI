package com.mineai.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognises notable blocks and groups connected ones into structures.
 *
 * <p>Classification uses block tags, block entities and a few concrete types,
 * so modded blocks are recognised too (anything with a block entity or an ore
 * tag shows up). Connected blocks of the same kind (logs, leaves, ores, water,
 * crops) are collapsed into one entry with a size and centre, which is far more
 * useful than dozens of single-block rows.</p>
 */
public final class StructureScanner {

    private static final int MAX_FEATURES = 48;
    private static final Set<String> CLUSTERABLE = Set.of("log", "leaves", "ore", "water", "crop", "flower");

    private StructureScanner() {
    }

    public static JsonArray features(Level level, BlockPos center, int radius) {
        int top = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 12);
        int bottom = Math.max(level.getMinBuildHeight(), center.getY() - 16);

        Map<BlockPos, String> notable = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -16, -radius),
                center.offset(radius, 12, radius))) {
            if (pos.getY() < bottom || pos.getY() > top) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            String kind = classify(level, pos, state);
            if (kind != null) {
                notable.put(pos.immutable(), kind);
            }
        }

        JsonArray features = new JsonArray();
        Set<BlockPos> consumed = new HashSet<>();

        for (Map.Entry<BlockPos, String> entry : notable.entrySet()) {
            if (features.size() >= MAX_FEATURES) {
                break;
            }
            if (consumed.contains(entry.getKey()) || !CLUSTERABLE.contains(entry.getValue())) {
                continue;
            }
            List<BlockPos> cluster = floodFill(notable, entry.getKey(), entry.getValue());
            consumed.addAll(cluster);
            features.add(clusterEntry(entry.getValue(), cluster, center));
        }

        for (Map.Entry<BlockPos, String> entry : notable.entrySet()) {
            if (features.size() >= MAX_FEATURES) {
                break;
            }
            if (consumed.contains(entry.getKey())) {
                continue;
            }
            features.add(individualEntry(level, entry.getKey(), entry.getValue(), center));
        }

        return features;
    }

    private static JsonObject clusterEntry(String kind, List<BlockPos> cluster, BlockPos center) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;

        for (BlockPos pos : cluster) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }

        int size = cluster.size();
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", kind);
        entry.addProperty("structure", true);
        entry.addProperty("blocks", size);
        entry.addProperty("x", (int) (sumX / size));
        entry.addProperty("y", (int) (sumY / size));
        entry.addProperty("z", (int) (sumZ / size));
        entry.addProperty("size_x", maxX - minX + 1);
        entry.addProperty("size_y", maxY - minY + 1);
        entry.addProperty("size_z", maxZ - minZ + 1);
        entry.addProperty("distance", Math.sqrt(center.distSqr(new BlockPos((int) (sumX / size), (int) (sumY / size), (int) (sumZ / size)))));
        return entry;
    }

    private static JsonObject individualEntry(Level level, BlockPos pos, String kind, BlockPos center) {
        BlockState state = level.getBlockState(pos);
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", kind);
        entry.addProperty("block", RegistryIds.block(state));
        entry.addProperty("x", pos.getX());
        entry.addProperty("y", pos.getY());
        entry.addProperty("z", pos.getZ());
        entry.addProperty("distance", Math.sqrt(center.distSqr(pos)));

        if (level.getBlockEntity(pos) instanceof Container container) {
            int filled = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    filled++;
                }
            }
            entry.addProperty("filled_slots", filled);
            entry.addProperty("size", container.getContainerSize());
        }
        return entry;
    }

    private static List<BlockPos> floodFill(Map<BlockPos, String> notable, BlockPos start, String kind) {
        List<BlockPos> cluster = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty() && cluster.size() < 256) {
            BlockPos pos = queue.poll();
            cluster.add(pos);
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos next = pos.offset(d[0], d[1], d[2]);
                if (seen.contains(next)) {
                    continue;
                }
                if (kind.equals(notable.get(next))) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return cluster;
    }

    private static String classify(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (level.getBlockEntity(pos) instanceof Container) {
            return "container";
        }
        if (block instanceof CraftingTableBlock) {
            return "crafting_table";
        }
        if (block instanceof FurnaceBlock || block instanceof BlastFurnaceBlock
                || block instanceof SmokerBlock) {
            return "furnace";
        }
        if (block instanceof DoorBlock || state.is(BlockTags.DOORS)) {
            return "door";
        }
        if (block instanceof BedBlock || state.is(BlockTags.BEDS)) {
            return "bed";
        }
        if (block instanceof FarmBlock) {
            return "farmland";
        }
        if (block instanceof SpawnerBlock) {
            return "spawner";
        }
        if (block == Blocks.WATER) {
            return "water";
        }
        if (block == Blocks.LAVA) {
            return "lava";
        }
        if (state.is(BlockTags.LOGS)) {
            return "log";
        }
        if (state.is(BlockTags.LEAVES)) {
            return "leaves";
        }
        if (state.is(BlockTags.CROPS)) {
            return "crop";
        }
        if (state.is(BlockTags.FLOWERS)) {
            return "flower";
        }
        if (state.is(BlockTags.SAPLINGS)) {
            return "sapling";
        }
        if (state.is(Tags.Blocks.ORES)) {
            return "ore";
        }
        if (state.is(BlockTags.CLIMBABLE)) {
            return "climbable";
        }
        if (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) {
            return "anvil";
        }
        if (block == Blocks.BELL) {
            return "bell";
        }
        if (state.hasBlockEntity() || block instanceof EntityBlock) {
            return "block_entity";
        }
        return null;
    }
}
