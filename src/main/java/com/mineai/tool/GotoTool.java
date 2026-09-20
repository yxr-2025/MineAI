package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.MoveAction;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class GotoTool implements AgentTool {

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public String description() {
        return "Walk to the block column at the given x/z coordinates and stand on the ground there. "
                + "The agent follows the terrain, so y is only a hint. Fails if the path is blocked or too slow.";
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
        Vec3 destination = new Vec3(pos.getX() + 0.5D, npc.getY(), pos.getZ() + 0.5D);
        npc.actionQueue().enqueue(new MoveAction(destination));
        return ToolResult.queued("walking to " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }
}
