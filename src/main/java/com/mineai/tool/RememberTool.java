package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class RememberTool implements AgentTool {

    @Override
    public String name() {
        return "remember";
    }

    @Override
    public String description() {
        return "Store a short fact for future sessions, for example the location of a resource or a useful coordinate.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject note = new JsonObject();
        note.addProperty("type", "string");
        note.addProperty("description", "fact to remember");
        properties.add("note", note);
        return schema(name(), description(), properties, "note");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String note = args.get("note").getAsString();
        return npc.agentController().remember(note);
    }
}
