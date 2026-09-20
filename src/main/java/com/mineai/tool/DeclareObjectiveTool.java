package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

/**
 * Lets the model state the success criterion. The controller verifies it
 * against the inventory, so "done" is never taken on trust.
 */
public final class DeclareObjectiveTool implements AgentTool {

    @Override
    public String name() {
        return "declare_objective";
    }

    @Override
    public String description() {
        return "State the concrete, checkable end state of the current goal, for example an item that must end up "
                + "in your inventory. The system verifies it and will not accept a finish claim until it is true.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id that must be in the inventory, for example mekanism:metallurgic_infuser");
        properties.add("item", item);
        properties.add("count", numberProperty("how many are needed (default 1)"));
        return schema(name(), description(), properties, "item");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String item = args.get("item").getAsString();
        int count = args.has("count") ? integer(args, "count") : 1;
        return npc.agentController().declareObjective(item, count);
    }
}
