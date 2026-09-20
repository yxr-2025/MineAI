package com.mineai.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class InventoryHelper {

    private InventoryHelper() {
    }

    public static ItemStack find(Player player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static int count(Player player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean has(Player player, ItemStack cost) {
        if (cost.isEmpty()) {
            return true;
        }
        return count(player, cost.getItem()) >= cost.getCount();
    }

    public static boolean consume(Player player, ItemStack cost) {
        if (cost.isEmpty()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int remaining = cost.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != cost.getItem()) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            inventory.removeItem(slot, take);
            remaining -= take;
        }
        return remaining == 0;
    }

    public static boolean hasIngredient(Player player, Ingredient ingredient) {
        if (ingredient.getItems().length == 0) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean consumeOne(Player player, Ingredient ingredient) {
        if (ingredient.getItems().length == 0) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                inventory.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }
}
