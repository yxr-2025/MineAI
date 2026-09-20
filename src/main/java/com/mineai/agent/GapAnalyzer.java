package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recursively subtracts the inventory from an item's recipe tree and produces a
 * flat "what is still missing" list. This removes the bookkeeping burden from
 * the model: it no longer has to track how many of each intermediate it lacks.
 */
public final class GapAnalyzer {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_ENTRIES = 24;

    private GapAnalyzer() {
    }

    public static JsonObject analyze(Level level, Player player, Item target, int count) {
        Map<String, Integer> need = new LinkedHashMap<>();
        Map<String, String> source = new LinkedHashMap<>();
        Map<String, Item> items = new LinkedHashMap<>();
        Map<String, JsonArray> anyOf = new LinkedHashMap<>();
        expand(level, player, target, count, need, source, items, anyOf, 0);

        JsonArray missing = new JsonArray();
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            Item item = items.get(entry.getKey());
            int have = item == null ? 0 : InventoryHelper.count(player, item);
            JsonObject row = new JsonObject();
            row.addProperty("item", entry.getKey());
            row.addProperty("need", entry.getValue() + have);
            row.addProperty("have", have);
            row.addProperty("missing", entry.getValue());
            row.addProperty("source", source.getOrDefault(entry.getKey(), "gather"));
            JsonArray alternatives = anyOf.get(entry.getKey());
            if (alternatives != null) {
                row.add("any_of", alternatives);
            }
            missing.add(row);
        }

        JsonObject result = new JsonObject();
        result.addProperty("target", RegistryIds.item(target));
        result.addProperty("target_count", count);
        result.add("missing", missing);
        result.addProperty("needs_crafting_table", needsTable(level, target));
        result.addProperty("needs_fuel_for_smelting", anySmelt(source));
        result.addProperty("hint",
                "Gather the missing items (smelt ores first if source=smelt), then craft in dependency order.");
        return result;
    }

    private static void expand(Level level, Player player, Item item, int count,
                               Map<String, Integer> need, Map<String, String> source,
                               Map<String, Item> items, Map<String, JsonArray> anyOf, int depth) {
        if (item == null || count <= 0) {
            return;
        }
        String id = RegistryIds.item(item);
        int have = InventoryHelper.count(player, item);
        int missing = count - have;
        if (missing <= 0) {
            return;
        }
        need.merge(id, missing, Integer::sum);
        items.put(id, item);
        if (depth >= MAX_DEPTH || need.size() > MAX_ENTRIES) {
            return;
        }

        RegistryAccess access = level.registryAccess();

        CraftingRecipe crafting = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(recipe -> !recipe.isSpecial())
                .filter(recipe -> !recipe.getResultItem(access).isEmpty())
                .filter(recipe -> recipe.getResultItem(access).getItem() == item)
                .findFirst()
                .orElse(null);
        if (crafting != null) {
            source.putIfAbsent(id, "craft");
            int yield = Math.max(1, crafting.getResultItem(access).getCount());
            int crafts = (missing + yield - 1) / yield;

            Map<Item, Integer> ingredients = new LinkedHashMap<>();
            for (Ingredient ingredient : crafting.getIngredients()) {
                if (ingredient.getItems().length == 0) {
                    continue;
                }
                Item representative = RecipeLookup.representative(ingredient, player);
                if (representative == null) {
                    continue;
                }
                ingredients.merge(representative, 1, Integer::sum);
                if (ingredient.getItems().length > 1) {
                    JsonArray alternatives = new JsonArray();
                    for (ItemStack stack : ingredient.getItems()) {
                        String alternativeId = RegistryIds.item(stack.getItem());
                        boolean present = false;
                        for (int i = 0; i < alternatives.size(); i++) {
                            if (alternatives.get(i).getAsString().equals(alternativeId)) {
                                present = true;
                                break;
                            }
                        }
                        if (!present) {
                            alternatives.add(alternativeId);
                        }
                    }
                    anyOf.put(RegistryIds.item(representative), alternatives);
                }
            }
            for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
                expand(level, player, entry.getKey(), entry.getValue() * crafts, need, source, items, anyOf, depth + 1);
            }
            return;
        }

        SmeltingRecipe smelting = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.SMELTING)
                .stream()
                .filter(recipe -> !recipe.getResultItem(access).isEmpty())
                .filter(recipe -> recipe.getResultItem(access).getItem() == item)
                .findFirst()
                .orElse(null);
        if (smelting != null) {
            source.putIfAbsent(id, "smelt");
            for (Ingredient ingredient : smelting.getIngredients()) {
                if (ingredient.getItems().length == 0) {
                    continue;
                }
                Item representative = RecipeLookup.representative(ingredient, player);
                if (representative != null) {
                    expand(level, player, representative, missing, need, source, items, anyOf, depth + 1);
                }
            }
            return;
        }

        source.putIfAbsent(id, "gather");
    }

    private static boolean needsTable(Level level, Item item) {
        RegistryAccess access = level.registryAccess();
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(recipe -> !recipe.getResultItem(access).isEmpty())
                .filter(recipe -> recipe.getResultItem(access).getItem() == item)
                .anyMatch(recipe -> recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped
                        && (shaped.getWidth() > 2 || shaped.getHeight() > 2));
    }

    private static boolean anySmelt(Map<String, String> source) {
        return source.containsValue("smelt");
    }

    public static String summarize(Level level, Player player, Item target, int count) {
        return analyze(level, player, target, count).toString();
    }

    public static ItemStack representative(Item item) {
        return new ItemStack(item);
    }
}
