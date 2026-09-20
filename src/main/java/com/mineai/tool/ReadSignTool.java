package com.mineai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ReadSignTool implements AgentTool {

    @Override
    public String name() {
        return "read_sign";
    }

    @Override
    public String description() {
        return "Read the text written on a sign at the given block coordinates.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("x", numberProperty("sign block x"));
        properties.add("y", numberProperty("sign block y"));
        properties.add("z", numberProperty("sign block z"));
        return schema(name(), description(), properties, "x", "y", "z");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        if (npc.distanceToSqr(Vec3.atCenterOf(pos)) > 36.0D) {
            return ToolResult.fail("sign is too far away");
        }
        if (!(npc.level().getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return ToolResult.fail("no sign at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        JsonObject data = new JsonObject();
        data.add("front", lines(sign.getFrontText()));
        data.add("back", lines(sign.getBackText()));
        return ToolResult.okData("read sign", data);
    }

    private static JsonArray lines(SignText text) {
        JsonArray array = new JsonArray();
        for (Component component : text.getMessages(false)) {
            String line = component.getString();
            if (!line.isBlank()) {
                array.add(line);
            }
        }
        return array;
    }
}
