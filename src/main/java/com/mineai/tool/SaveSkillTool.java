package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class SaveSkillTool implements AgentTool {

    @Override
    public String name() {
        return "save_skill";
    }

    @Override
    public String description() {
        return "Save the tool calls of the current run as a named, reusable skill for later sessions.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject name = new JsonObject();
        name.addProperty("type", "string");
        name.addProperty("description", "short skill name");
        properties.add("name", name);
        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("description", "what this skill accomplishes");
        properties.add("description", description);
        return schema(name(), description(), properties, "name");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String name = args.get("name").getAsString();
        String description = args.has("description") ? args.get("description").getAsString() : name;
        return npc.agentController().saveCurrentTraceAsSkill(name, description);
    }
}
