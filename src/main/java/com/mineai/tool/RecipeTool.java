package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.agent.RecipeLookup;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.world.item.Item;

import static com.mineai.tool.AgentTool.schema;

public final class RecipeTool implements AgentTool {

    @Override
    public String name() {
        return "recipe_for";
    }

    @Override
    public String description() {
        return "Look up how an item is made: crafting ingredients (with counts), whether a crafting table is "
                + "required, and the smelting recipe if it has one. Use this instead of guessing.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id, for example mekanism:metallurgic_infuser");
        properties.add("item", item);
        return schema(name(), description(), properties, "item");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String itemId = args.get("item").getAsString();
        Item item = RegistryIds.itemById(itemId);
        if (item == null) {
            return ToolResult.fail("unknown item: " + itemId);
        }
        JsonObject data = RecipeLookup.describe(npc.level(), item);
        if (!data.has("crafting") && !data.has("smelting")) {
            return ToolResult.fail("no crafting or smelting recipe found for " + itemId);
        }
        return ToolResult.okData("recipe for " + itemId, data);
    }
}
