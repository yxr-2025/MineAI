package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.schema;

public final class CraftTool implements AgentTool {

    private static final int MAX_CRAFTS_PER_CALL = 64;

    @Override
    public String name() {
        return "craft";
    }

    @Override
    public String description() {
        return "Craft an item from ingredients already in the agent inventory. "
                + "Recipes wider or taller than 2x2 require a crafting table within 5 blocks. "
                + "Provide the item id of the result and optionally how many times to craft it.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "result item id, for example minecraft:stick");
        properties.add("item", item);
        properties.add("count", AgentTool.numberProperty("how many times to craft (default 1)"));
        return schema(name(), description(), properties, "item");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String itemId = args.get("item").getAsString();
        int requested = args.has("count") ? Math.max(1, integer(args, "count")) : 1;
        requested = Math.min(requested, MAX_CRAFTS_PER_CALL);

        Item target = RegistryIds.itemById(itemId);
        if (target == null) {
            return ToolResult.fail("unknown item: " + itemId);
        }

        Level level = npc.level();
        RegistryAccess access = level.registryAccess();

        CraftingRecipe recipe = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(candidate -> !candidate.isSpecial())
                .filter(candidate -> !candidate.getResultItem(access).isEmpty())
                .filter(candidate -> candidate.getResultItem(access).getItem() == target)
                .filter(candidate -> canCraft(npc, candidate))
                .findFirst()
                .orElse(null);

        if (recipe == null) {
            return ToolResult.fail("no craftable recipe for " + itemId
                    + " with the current inventory");
        }

        if (needsCraftingTable(recipe) && !hasCraftingTableNearby(npc, 5)) {
            return ToolResult.fail("this recipe needs a crafting table within 5 blocks; none found");
        }

        int crafted = 0;
        int produced = 0;
        for (int i = 0; i < requested; i++) {
            if (!canCraft(npc, recipe)) {
                break;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.getItems().length == 0) {
                    continue;
                }
                InventoryHelper.consumeOne(npc, ingredient);
            }
            ItemStack result = recipe.getResultItem(access).copy();
            int yield = result.getCount();
            if (!npc.getInventory().add(result)) {
                return crafted > 0
                        ? ToolResult.ok("crafted " + crafted + " time(s), " + produced
                                + " item(s); stopped because the inventory is full")
                        : ToolResult.fail("inventory is full");
            }
            crafted++;
            produced += yield;
        }

        if (crafted == 0) {
            return ToolResult.fail("missing ingredients for " + itemId);
        }
        return ToolResult.ok("crafted " + produced + " " + itemId + " (" + crafted + " craft(s))");
    }

    private static boolean needsCraftingTable(CraftingRecipe recipe) {
        return recipe instanceof ShapedRecipe shaped
                && (shaped.getWidth() > 2 || shaped.getHeight() > 2);
    }

    private static boolean hasCraftingTableNearby(AgentPlayer npc, int radius) {
        Level level = npc.level();
        BlockPos center = npc.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -2, -radius),
                center.offset(radius, 2, radius))) {
            if (level.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE) {
                return true;
            }
        }
        return false;
    }

    private static boolean canCraft(AgentPlayer npc, CraftingRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.getItems().length == 0) {
                continue;
            }
            if (!InventoryHelper.hasIngredient(npc, ingredient)) {
                return false;
            }
        }
        return true;
    }
}
