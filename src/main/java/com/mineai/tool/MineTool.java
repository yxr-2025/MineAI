package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.ProgressiveMineAction;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class MineTool implements AgentTool {

    @Override
    public String name() {
        return "mine";
    }

    @Override
    public String description() {
        return "Break the block at the given absolute block coordinates. The block must be within about 6 blocks of the agent.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("x", numberProperty("block x"));
        properties.add("y", numberProperty("block y"));
        properties.add("z", numberProperty("block z"));
        return schema(name(), description(), properties, "x", "y", "z");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));

        // Self-trapping guard: breaking the block under your own feet drops you
        // into a one-wide shaft you cannot climb out of. Mine beside you and
        // step down instead, which naturally produces a staircase.
        BlockPos feet = npc.blockPosition();
        if (pos.equals(feet.below())) {
            return ToolResult.fail("that block is directly under your feet; mining it would trap you in a "
                    + "one-wide shaft. Mine the block beside you and step down instead.");
        }

        npc.actionQueue().enqueue(new ProgressiveMineAction(pos));
        return ToolResult.queued("mining " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }
}
