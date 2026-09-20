package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.PlaceAction;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class PlaceTool implements AgentTool {

    @Override
    public String name() {
        return "place";
    }

    @Override
    public String description() {
        return "Place the block held in the main hand at the given absolute block coordinates. The target must be air or replaceable and have an adjacent solid support.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("x", numberProperty("target block x"));
        properties.add("y", numberProperty("target block y"));
        properties.add("z", numberProperty("target block z"));
        return schema(name(), description(), properties, "x", "y", "z");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        npc.actionQueue().enqueue(new PlaceAction(pos));
        return ToolResult.queued("placing at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }
}
