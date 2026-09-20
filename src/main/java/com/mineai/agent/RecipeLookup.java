package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.util.RegistryIds;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the world's recipe manager into compact, model-readable data so the
 * planner does not have to guess how something is made.
 */
public final class RecipeLookup {

    private RecipeLookup() {
    }

    public static JsonObject describe(Level level, Item item) {
        JsonObject data = new JsonObject();
        data.addProperty("item", RegistryIds.item(item));

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
            JsonObject entry = new JsonObject();
            ItemStack result = crafting.getResultItem(access);
            entry.addProperty("result", RegistryIds.item(result.getItem()));
            entry.addProperty("result_count", result.getCount());
            entry.addProperty("needs_crafting_table",
                    crafting instanceof ShapedRecipe shaped && (shaped.getWidth() > 2 || shaped.getHeight() > 2));

            entry.add("ingredients", describeIngredients(crafting.getIngredients()));
            data.add("crafting", entry);
        }

        SmeltingRecipe smelting = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.SMELTING)
                .stream()
                .filter(recipe -> !recipe.getResultItem(access).isEmpty())
                .filter(recipe -> recipe.getResultItem(access).getItem() == item)
                .findFirst()
                .orElse(null);

        if (smelting != null) {
            JsonObject entry = new JsonObject();
            ItemStack result = smelting.getResultItem(access);
            entry.addProperty("result", RegistryIds.item(result.getItem()));
            entry.addProperty("result_count", result.getCount());
            JsonArray inputs = new JsonArray();
            for (Ingredient ingredient : smelting.getIngredients()) {
                if (ingredient.getItems().length == 0) {
                    continue;
                }
                inputs.add(RegistryIds.item(ingredient.getItems()[0].getItem()));
            }
            entry.add("inputs", inputs);
            entry.addProperty("requires_fuel", true);
            data.add("smelting", entry);
        }

        return data;
    }

    /**
     * Aggregates ingredients. A tag ingredient (planks, logs, ...) has many
     * possible items; reporting only the first one makes the agent hunt for a
     * specific wood type, so tags are reported as {@code any_of}.
     */
    public static JsonArray describeIngredients(List<Ingredient> ingredients) {
        Map<String, JsonObject> aggregated = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.getItems().length == 0) {
                continue;
            }
            String key;
            JsonObject entry = new JsonObject();
            if (ingredient.getItems().length == 1) {
                String id = RegistryIds.item(ingredient.getItems()[0].getItem());
                key = "item:" + id;
                entry.addProperty("item", id);
            } else {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (ItemStack stack : ingredient.getItems()) {
                    String id = RegistryIds.item(stack.getItem());
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
                java.util.Collections.sort(ids);
                key = "any:" + String.join(",", ids);
                JsonArray anyOf = new JsonArray();
                for (String id : ids) {
                    anyOf.add(id);
                }
                entry.add("any_of", anyOf);
            }
            JsonObject existing = aggregated.get(key);
            if (existing != null) {
                existing.addProperty("count", existing.get("count").getAsInt() + 1);
            } else {
                entry.addProperty("count", 1);
                aggregated.put(key, entry);
            }
        }
        JsonArray array = new JsonArray();
        for (JsonObject entry : aggregated.values()) {
            array.add(entry);
        }
        return array;
    }

    /**
     * A concrete item to use for an ingredient, preferring something the player
     * already has so it does not go looking for a wood type it lacks.
     */
    public static Item representative(Ingredient ingredient, net.minecraft.world.entity.player.Player player) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return null;
        }
        if (player != null) {
            for (ItemStack stack : stacks) {
                if (com.mineai.util.InventoryHelper.count(player, stack.getItem()) > 0) {
                    return stack.getItem();
                }
            }
        }
        return stacks[0].getItem();
    }

    public static boolean satisfiedByAny(Ingredient ingredient, net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : ingredient.getItems()) {
            if (com.mineai.util.InventoryHelper.count(player, stack.getItem()) > 0) {
                return true;
            }
        }
        return false;
    }

    public static CraftingRecipe findCrafting(Level level, Item item) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(recipe -> !recipe.isSpecial())
                .filter(recipe -> !recipe.getResultItem(level.registryAccess()).isEmpty())
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).getItem() == item)
                .findFirst()
                .orElse(null);
    }

    public static SmeltingRecipe findSmelting(Level level, Item item) {
        return level.getRecipeManager()
                .getAllRecipesFor(RecipeType.SMELTING)
                .stream()
                .filter(recipe -> !recipe.getResultItem(level.registryAccess()).isEmpty())
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).getItem() == item)
                .findFirst()
                .orElse(null);
    }

    public static boolean needsTable(Level level, Item item) {
        CraftingRecipe recipe = findCrafting(level, item);
        return recipe instanceof ShapedRecipe shaped && (shaped.getWidth() > 2 || shaped.getHeight() > 2);
    }

    public static String summarize(Level level, Item item) {
        return describe(level, item).toString();
    }
}

