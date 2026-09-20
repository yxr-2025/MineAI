package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.WaitAction;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class WaitTool implements AgentTool {

    @Override
    public String name() {
        return "wait";
    }

    @Override
    public String description() {
        return "Do nothing for a number of ticks while a world mechanism finishes (for example a furnace smelting).";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("ticks", numberProperty("ticks to wait, 1 to 1200"));
        return schema(name(), description(), properties, "ticks");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        int ticks = Math.max(1, Math.min(1200, integer(args, "ticks")));
        npc.actionQueue().enqueue(new WaitAction(ticks));
        return ToolResult.queued("waiting " + ticks + " ticks");
    }
}
