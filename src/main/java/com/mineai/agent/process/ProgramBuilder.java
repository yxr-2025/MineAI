package com.mineai.agent.process;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.agent.RecipeLookup;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.MiningKnowledge;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds deterministic programs for common procedures.
 *
 * <p>Procedures are not decisions: given "get 4 iron ingots" the sequence of
 * gathering, tool prerequisites, crafting, smelting and waiting is mechanical,
 * the same way pathfinding is. The model still decides <em>what</em> to obtain
 * and <em>why</em>.</p>
 *
 * <p>Steps are emitted in the same JSON shape as recorded skills
 * ({@code {"tool":..., "args":{...}}}), so they run through the existing
 * deterministic skill executor.</p>
 */
public final class ProgramBuilder {

    private static final int MAX_SOURCES = 24;

    private ProgramBuilder() {
    }

    // ------------------------------------------------------------- crafting

    public static List<JsonObject> craft(AgentPlayer npc, Item target, int count) {
        List<JsonObject> program = new ArrayList<>();
        CraftingRecipe recipe = RecipeLookup.findCrafting(npc.level(), target);
        if (recipe == null) {
            return program;
        }
        int yield = Math.max(1, recipe.getResultItem(npc.level().registryAccess()).getCount());
        int crafts = (count + yield - 1) / yield;

        if (RecipeLookup.needsTable(npc.level(), target)) {
            BlockPos table = findOrPlace(npc, program, Blocks.CRAFTING_TABLE);
            if (table == null) {
                return program;
            }
        }
        program.add(step("craft", argsItem(RegistryIds.item(target), crafts)));
        return program;
    }

    // ------------------------------------------------------------- smelting

    public static List<JsonObject> smelt(AgentPlayer npc, Item target, int count) {
        List<JsonObject> program = new ArrayList<>();
        SmeltingRecipe recipe = RecipeLookup.findSmelting(npc.level(), target);
        if (recipe == null) {
            return program;
        }
        Ingredient input = recipe.getIngredients().isEmpty() ? null : recipe.getIngredients().get(0);
        Item inputItem = input == null ? null : RecipeLookup.representative(input, npc);
        if (inputItem == null) {
            return program;
        }

        BlockPos furnace = findOrPlace(npc, program, Blocks.FURNACE);
        if (furnace == null) {
            return program;
        }

        program.add(step("insert_item", containerArgs(furnace, 0, RegistryIds.item(inputItem), count)));
        if (!InventoryHelper.has(npc, new ItemStack(net.minecraft.world.item.Items.COAL))) {
            program.addAll(gather(npc, net.minecraft.world.item.Items.COAL, 1));
        }
        program.add(step("insert_item", containerArgs(furnace, 1,
                RegistryIds.item(net.minecraft.world.item.Items.COAL), 1)));

        // wait for the furnace, then take the result out
        program.add(step("wait", argsNumber("ticks", 220 + count * 200)));
        program.add(step("extract_item", containerArgs(furnace, 2, "", count)));
        return program;
    }

    // -------------------------------------------------------------- gather

    /**
     * Mines the blocks that drop the item, up to the required count, then walks
     * onto the drops to pick them up.
     */
    public static List<JsonObject> gather(AgentPlayer npc, Item item, int count) {
        List<JsonObject> program = new ArrayList<>();
        String itemId = RegistryIds.item(item);
        int have = InventoryHelper.count(npc, item);
        int needed = count - have;
        if (needed <= 0) {
            return program;
        }

        List<BlockPos> sources = findSources(npc, itemId, needed);
        if (sources.isEmpty()) {
            return program;
        }
        // One block per needed item, plus a little slack for misses.
        int take = Math.min(sources.size(), needed + 2);
        for (int i = 0; i < take; i++) {
            BlockPos pos = sources.get(i);
            program.add(step("goto", argsPos(pos, 0, -1, 0)));
            program.add(step("mine", argsPos(pos)));
            program.add(step("goto", argsPos(pos)));
        }
        return program;
    }

    /**
     * Ensures a tool of the given kind and tier exists in the inventory.
     * Tool prerequisites are mechanical: to mine iron you need a stone pickaxe,
     * to get a stone pickaxe you need cobblestone, and so on.
     */
    public static List<JsonObject> ensureTool(AgentPlayer npc, MiningKnowledge.ToolKind kind,
                                              MiningKnowledge.Tier tier) {
        List<JsonObject> program = new ArrayList<>();
        if (kind == MiningKnowledge.ToolKind.NONE || tier == MiningKnowledge.Tier.HAND) {
            return program;
        }
        String toolId = toolId(kind, tier);
        if (toolId == null) {
            return program;
        }
        Item tool = RegistryIds.itemById(toolId);
        if (tool == null) {
            return program;
        }
        if (InventoryHelper.count(npc, tool) > 0) {
            program.add(step("equip", argsItem(toolId, 0)));
            return program;
        }

        // material prerequisites, lowest tier first
        switch (tier) {
            case STONE -> {
                program.addAll(ensureTool(npc, MiningKnowledge.ToolKind.PICKAXE, MiningKnowledge.Tier.WOOD));
                program.addAll(gather(npc, net.minecraft.world.item.Items.COBBLESTONE, 3));
            }
            case IRON -> {
                program.addAll(ensureTool(npc, MiningKnowledge.ToolKind.PICKAXE, MiningKnowledge.Tier.STONE));
                program.addAll(gather(npc, net.minecraft.world.item.Items.RAW_IRON, 3));
                program.addAll(smelt(npc, net.minecraft.world.item.Items.IRON_INGOT, 3));
            }
            case DIAMOND -> {
                program.addAll(ensureTool(npc, MiningKnowledge.ToolKind.PICKAXE, MiningKnowledge.Tier.IRON));
                program.addAll(gather(npc, net.minecraft.world.item.Items.DIAMOND, 3));
            }
            case WOOD -> program.addAll(gather(npc, net.minecraft.world.item.Items.OAK_LOG, 3));
            default -> {
            }
        }

        // the crafting table itself needs wood
        if (RecipeLookup.needsTable(npc.level(), tool)
                && InventoryHelper.count(npc, net.minecraft.world.item.Items.OAK_PLANKS) < 4) {
            program.addAll(gather(npc, net.minecraft.world.item.Items.OAK_LOG, 1));
        }
        program.addAll(craft(npc, tool, 1));
        program.add(step("equip", argsItem(toolId, 0)));
        return program;
    }

    // ------------------------------------------------------------- helpers

    private static List<BlockPos> findSources(AgentPlayer npc, String itemId, int needed) {
        Level level = npc.level();
        ItemStack tool = npc.getMainHandItem();
        List<BlockPos> sources = new ArrayList<>(
                MiningKnowledge.findSources(level, npc.blockPosition(), 12, 6, npc, tool, itemId, MAX_SOURCES));
        // prefer sources whose tool requirement is already satisfied
        sources.sort((a, b) -> Boolean.compare(
                !MiningKnowledge.canHarvest(tool, level.getBlockState(a)),
                !MiningKnowledge.canHarvest(tool, level.getBlockState(b))));
        return sources;
    }

    /**
     * Finds a workstation nearby, or plans to place one. Returns its position,
     * or null when it cannot be arranged.
     */
    private static BlockPos findOrPlace(AgentPlayer npc, List<JsonObject> program, net.minecraft.world.level.block.Block block) {
        BlockPos found = findBlock(npc, block, 8);
        if (found != null) {
            return found;
        }
        Item item = block.asItem();
        if (InventoryHelper.count(npc, item) == 0) {
            program.addAll(craft(npc, item, 1));
        }
        if (InventoryHelper.count(npc, item) == 0) {
            return null;
        }
        BlockPos spot = npc.blockPosition().offset(2, 0, 0);
        program.add(step("equip", argsItem(RegistryIds.item(item), 0)));
        program.add(step("place", argsPos(spot)));
        return spot;
    }

    private static BlockPos findBlock(AgentPlayer npc, net.minecraft.world.level.block.Block block, int radius) {
        BlockPos center = npc.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -3, -radius), center.offset(radius, 3, radius))) {
            BlockState state = npc.level().getBlockState(pos);
            if (state.getBlock() == block) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static String toolId(MiningKnowledge.ToolKind kind, MiningKnowledge.Tier tier) {
        String material = switch (tier) {
            case WOOD -> "wooden";
            case STONE -> "stone";
            case IRON -> "iron";
            case DIAMOND -> "diamond";
            case NETHERITE -> "netherite";
            default -> null;
        };
        if (material == null) {
            return null;
        }
        String suffix = switch (kind) {
            case PICKAXE -> "pickaxe";
            case AXE -> "axe";
            case SHOVEL -> "shovel";
            case HOE -> "hoe";
            default -> null;
        };
        return suffix == null ? null : "minecraft:" + material + "_" + suffix;
    }

    private static JsonObject step(String tool, JsonObject args) {
        JsonObject step = new JsonObject();
        step.addProperty("tool", tool);
        step.add("args", args);
        return step;
    }

    private static JsonObject argsItem(String item, int count) {
        JsonObject args = new JsonObject();
        args.addProperty("item", item);
        if (count > 0) {
            args.addProperty("count", count);
        }
        return args;
    }

    private static JsonObject argsPos(BlockPos pos) {
        return argsPos(pos, 0, 0, 0);
    }

    private static JsonObject argsPos(BlockPos pos, int dx, int dy, int dz) {
        JsonObject args = new JsonObject();
        args.addProperty("x", pos.getX() + dx);
        args.addProperty("y", pos.getY() + dy);
        args.addProperty("z", pos.getZ() + dz);
        return args;
    }

    private static JsonObject containerArgs(BlockPos pos, int slot, String item, int count) {
        JsonObject args = new JsonObject();
        args.addProperty("x", pos.getX());
        args.addProperty("y", pos.getY());
        args.addProperty("z", pos.getZ());
        args.addProperty("slot", slot);
        if (!item.isBlank()) {
            args.addProperty("item", item);
        }
        args.addProperty("count", count);
        return args;
    }

    private static JsonObject argsNumber(String key, int value) {
        JsonObject args = new JsonObject();
        args.addProperty(key, value);
        return args;
    }

    public static Direction facing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
