package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.WorldStateCollector;

import static com.mineai.tool.AgentTool.schema;

public final class CollectStateTool implements AgentTool {

    @Override
    public String name() {
        return "collect_state";
    }

    @Override
    public String description() {
        return "Read a full snapshot of the agent: position, health, inventory, nearby blocks, nearby entities and environment.";
    }

    @Override
    public JsonObject parameters() {
        return schema(name(), description(), new JsonObject());
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        return ToolResult.okData("collected state", WorldStateCollector.collect(npc));
    }
}
