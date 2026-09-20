package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.integration.MachineAdapter;
import com.mineai.integration.MachineDescription;
import com.mineai.integration.MachineOperation;
import com.mineai.integration.MachineResult;
import com.mineai.integration.SlotInfo;
import net.minecraft.core.BlockPos;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ListContainerTool implements AgentTool {

    @Override
    public String name() {
        return "list_container";
    }

    @Override
    public String description() {
        return "List the contents and slot layout of a container or machine at the given block coordinates. "
                + "The block must be within about 6 blocks.";
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
        MachineAdapter adapter = ContainerSupport.adapterAt(npc, pos);
        if (adapter == null) {
            return ToolResult.fail("no container within reach at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        MachineDescription description = adapter.describe(ContainerSupport.context(npc, pos));
        MachineResult contents = adapter.execute(npc, ContainerSupport.context(npc, pos),
                new MachineOperation.QueryContents());

        JsonObject data = new JsonObject();
        data.addProperty("machine", description.id());
        data.addProperty("display_name", description.displayName());
        com.google.gson.JsonArray slots = new com.google.gson.JsonArray();
        for (SlotInfo slot : description.slots()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", slot.index());
            entry.addProperty("name", slot.name());
            entry.addProperty("type", slot.type());
            slots.add(entry);
        }
        data.add("slots", slots);
        if (contents.data() != null) {
            data.add("contents", contents.data());
        }
        return ToolResult.okData(contents.message(), data);
    }
}
