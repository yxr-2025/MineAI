package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

/**
 * Puts an item into the main hand. The model decides what to hold; this tool
 * only performs the mechanical swap.
 */
public final class EquipTool implements AgentTool {

    @Override
    public String name() {
        return "equip";
    }

    @Override
    public String description() {
        return "Hold an item in the main hand, either by hotbar slot (0-8) or by item id. "
                + "Use this before mining, attacking or eating.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("slot", numberProperty("hotbar slot 0-8"));
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id such as minecraft:iron_pickaxe");
        properties.add("item", item);
        return schema(name(), description(), properties);
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        Inventory inventory = npc.getInventory();

        if (args.has("slot") && !args.get("slot").isJsonNull()) {
            int slot = integer(args, "slot");
            if (slot < 0 || slot > 8) {
                return ToolResult.fail("hotbar slot must be between 0 and 8");
            }
            inventory.selected = slot;
            return ToolResult.ok("holding " + describe(inventory.getItem(slot)) + " from slot " + slot);
        }

        if (args.has("item") && !args.get("item").isJsonNull()) {
            String itemId = args.get("item").getAsString();
            Item item = RegistryIds.itemById(itemId);
            if (item == null) {
                return ToolResult.fail("unknown item: " + itemId);
            }
            int found = -1;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    found = slot;
                    break;
                }
            }
            if (found < 0) {
                return ToolResult.fail("inventory has no " + itemId);
            }
            if (found < 9) {
                inventory.selected = found;
            } else {
                int hotbar = inventory.selected;
                ItemStack current = inventory.getItem(hotbar);
                inventory.setItem(hotbar, inventory.getItem(found));
                inventory.setItem(found, current);
            }
            return ToolResult.ok("holding " + itemId);
        }

        return ToolResult.fail("provide either slot or item");
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : RegistryIds.item(stack.getItem()) + " x" + stack.getCount();
    }
}
