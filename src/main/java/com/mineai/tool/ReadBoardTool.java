package com.mineai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.agent.SharedBlackboard;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class ReadBoardTool implements AgentTool {

    @Override
    public String name() {
        return "read_board";
    }

    @Override
    public String description() {
        return "Read everything other agents have shared on the blackboard.";
    }

    @Override
    public JsonObject parameters() {
        return schema(name(), description(), new JsonObject());
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        JsonArray entries = SharedBlackboard.toJson();
        JsonObject data = new JsonObject();
        data.add("entries", entries);
        return ToolResult.okData("read " + entries.size() + " shared fact(s)", data);
    }
}
