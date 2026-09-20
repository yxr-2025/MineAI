package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.UseAction;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class UseTool implements AgentTool {

    @Override
    public String name() {
        return "use";
    }

    @Override
    public String description() {
        return "Use the item in the main hand without targeting a block (eating, throwing, activating an item).";
    }

    @Override
    public JsonObject parameters() {
        return schema(name(), description(), new JsonObject());
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        npc.actionQueue().enqueue(new UseAction());
        return ToolResult.queued("using main hand item");
    }
}
