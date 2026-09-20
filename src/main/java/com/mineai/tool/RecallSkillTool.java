package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class RecallSkillTool implements AgentTool {

    @Override
    public String name() {
        return "recall_skill";
    }

    @Override
    public String description() {
        return "Return the stored steps of a previously learned skill so it can be adapted.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject name = new JsonObject();
        name.addProperty("type", "string");
        name.addProperty("description", "skill name to recall");
        properties.add("name", name);
        return schema(name(), description(), properties, "name");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String name = args.get("name").getAsString();
        return npc.agentController().recallSkill(name);
    }
}
