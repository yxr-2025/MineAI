package com.mineai.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact top-down terrain summary. Produces a surface map plus a relative
 * height map so the model can reason about the local shape of the world
 * without receiving thousands of block entries.
 */
public final class TerrainScanner {

    private static final char AIR_CODE = '.';
    private static final String CODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private TerrainScanner() {
    }

    public static JsonObject scan(Level level, BlockPos center, int radius) {
        int size = radius * 2 + 1;
        Map<String, Character> legend = new LinkedHashMap<>();
        legend.put("minecraft:air", AIR_CODE);

        String[][] surfaceRows = new String[size][size];
        int[][] surfaceHeights = new int[size][size];
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        int top = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 12);
        int bottom = Math.max(level.getMinBuildHeight(), center.getY() - 24);

        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int x = center.getX() - radius + dx;
                int z = center.getZ() - radius + dz;

                BlockState surface = null;
                int surfaceY = bottom;
                for (int y = top; y >= bottom; y--) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir()) {
                        surface = state;
                        surfaceY = y;
                        break;
                    }
                }

                if (surface == null) {
                    surfaceRows[dz][dx] = String.valueOf(AIR_CODE);
                    surfaceHeights[dz][dx] = bottom;
                    minHeight = Math.min(minHeight, bottom);
                    maxHeight = Math.max(maxHeight, bottom);
                } else {
                    String id = RegistryIds.block(surface);
                    surfaceRows[dz][dx] = String.valueOf(codeFor(id, legend));
                    surfaceHeights[dz][dx] = surfaceY;
                    minHeight = Math.min(minHeight, surfaceY);
                    maxHeight = Math.max(maxHeight, surfaceY);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("origin_x", center.getX() - radius);
        result.addProperty("origin_z", center.getZ() - radius);
        result.addProperty("radius", radius);
        result.addProperty("base_y", minHeight);
        result.addProperty("min_surface_y", minHeight);
        result.addProperty("max_surface_y", maxHeight);

        JsonArray surface = new JsonArray();
        JsonArray heights = new JsonArray();
        for (int dz = 0; dz < size; dz++) {
            StringBuilder surfaceRow = new StringBuilder(size);
            StringBuilder heightRow = new StringBuilder(size);
            for (int dx = 0; dx < size; dx++) {
                surfaceRow.append(surfaceRows[dz][dx]);
                heightRow.append(Integer.toString(surfaceHeights[dz][dx] - minHeight, 36));
            }
            surface.add(surfaceRow.toString());
            heights.add(heightRow.toString());
        }
        result.add("surface", surface);
        result.add("height", heights);

        JsonObject legendJson = new JsonObject();
        for (Map.Entry<String, Character> entry : legend.entrySet()) {
            legendJson.addProperty(String.valueOf(entry.getValue()), entry.getKey());
        }
        result.add("legend", legendJson);

        JsonObject hint = new JsonObject();
        hint.addProperty("row_axis", "z (north first)");
        hint.addProperty("column_axis", "x (west first)");
        hint.addProperty("height_encoding", "base36 of (surface_y - base_y)");
        result.add("_format", hint);

        return result;
    }

    /**
     * One-line summary for the system prompt.
     */
    public static String summarize(Level level, BlockPos center, int radius) {
        JsonObject scan = scan(level, center, radius);
        StringBuilder builder = new StringBuilder();
        builder.append("surface rows (north->south), legend ");
        builder.append(scan.getAsJsonObject("legend"));
        builder.append('\n');
        JsonArray surface = scan.getAsJsonArray("surface");
        for (int i = 0; i < surface.size(); i++) {
            builder.append(surface.get(i).getAsString());
            if (i < surface.size() - 1) {
                builder.append('\n');
            }
        }
        builder.append("\nheight delta rows (base36, base_y=")
                .append(scan.get("base_y").getAsInt())
                .append(")\n");
        JsonArray heights = scan.getAsJsonArray("height");
        for (int i = 0; i < heights.size(); i++) {
            builder.append(heights.get(i).getAsString());
            if (i < heights.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static char codeFor(String id, Map<String, Character> legend) {
        Character existing = legend.get(id);
        if (existing != null) {
            return existing;
        }
        int index = legend.size() - 1;
        char code = index < CODE_ALPHABET.length() ? CODE_ALPHABET.charAt(index) : '?';
        legend.put(id, code);
        return code;
    }
}
