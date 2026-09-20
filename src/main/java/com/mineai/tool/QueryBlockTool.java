package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class QueryBlockTool implements AgentTool {

    @Override
    public String name() {
        return "query_block";
    }

    @Override
    public String description() {
        return "Read the block at the given absolute block coordinates.";
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
        BlockState state = npc.level().getBlockState(pos);

        JsonObject data = new JsonObject();
        data.addProperty("x", pos.getX());
        data.addProperty("y", pos.getY());
        data.addProperty("z", pos.getZ());
        data.addProperty("block", RegistryIds.block(state));
        data.addProperty("is_air", state.isAir());
        data.addProperty("has_block_entity", state.hasBlockEntity());
        return ToolResult.okData("queried block", data);
    }
}
