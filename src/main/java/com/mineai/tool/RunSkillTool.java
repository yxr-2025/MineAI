package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

/**
 * Runs a saved skill as a deterministic program. The whole procedure executes
 * without further model calls; if a step fails, control returns to the model.
 */
public final class RunSkillTool implements AgentTool {

    @Override
    public String name() {
        return "run_skill";
    }

    @Override
    public String description() {
        return "Run a saved skill as a program. It replays the whole procedure without further decisions and only "
                + "comes back to you if a step fails. Coordinates are translated relative to the anchor.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject name = new JsonObject();
        name.addProperty("type", "string");
        name.addProperty("description", "skill name to run");
        properties.add("name", name);
        properties.add("x", numberProperty("anchor block x (default: current position)"));
        properties.add("y", numberProperty("anchor block y (default: current position)"));
        properties.add("z", numberProperty("anchor block z (default: current position)"));
        return schema(name(), description(), properties, "name");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String name = args.get("name").getAsString();
        BlockPos anchor = args.has("x") && args.has("y") && args.has("z")
                ? new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"))
                : npc.blockPosition();
        return npc.agentController().startSkill(name, anchor);
    }
}
