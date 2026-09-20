package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.agent.CandidateActions;
import com.mineai.entity.AgentPlayer;

import static com.mineai.tool.AgentTool.schema;

public final class CandidatesTool implements AgentTool {

    @Override
    public String name() {
        return "candidates";
    }

    @Override
    public String description() {
        return "List the concrete actions that are relevant right now, grounded in the objective, your inventory "
                + "and nearby sources. Prefer picking from this list over inventing an action.";
    }

    @Override
    public JsonObject parameters() {
        return schema(name(), description(), new JsonObject());
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        return ToolResult.okData("current candidates",
                CandidateActions.build(npc, npc.agentController().currentObjective()));
    }
}
