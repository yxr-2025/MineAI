package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.agent.SharedBlackboard;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class ShareTool implements AgentTool {

    @Override
    public String name() {
        return "share";
    }

    @Override
    public String description() {
        return "Publish a fact to the shared blackboard so other agents can use it, "
                + "for example a resource location or a danger warning.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject key = new JsonObject();
        key.addProperty("type", "string");
        key.addProperty("description", "short fact key, for example iron_ore or danger");
        properties.add("key", key);
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("description", "fact value, for example coordinates and details");
        properties.add("value", value);
        return schema(name(), description(), properties, "key", "value");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String key = args.get("key").getAsString();
        String value = args.get("value").getAsString();
        SharedBlackboard.put(key, value, npc.npcId());
        return ToolResult.ok("shared '" + key + "' = " + value);
    }
}
