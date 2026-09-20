package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.agent.GapAnalyzer;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.world.item.Item;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

/**
 * Flat "what is still missing" list for an item, computed by subtracting the
 * inventory from the full recipe tree. Saves the model from bookkeeping.
 */
public final class MissingForTool implements AgentTool {

    @Override
    public String name() {
        return "missing_for";
    }

    @Override
    public String description() {
        return "List exactly what is still missing to make an item, including intermediate materials and whether "
                + "each one must be crafted, smelted or gathered.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id to reach");
        properties.add("item", item);
        properties.add("count", numberProperty("how many are needed (default 1)"));
        return schema(name(), description(), properties, "item");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String itemId = args.get("item").getAsString();
        Item item = RegistryIds.itemById(itemId);
        if (item == null) {
            return ToolResult.fail("unknown item: " + itemId);
        }
        int count = args.has("count") ? integer(args, "count") : 1;
        return ToolResult.okData("missing materials for " + count + "x " + itemId,
                GapAnalyzer.analyze(npc.level(), npc, item, count));
    }
}
