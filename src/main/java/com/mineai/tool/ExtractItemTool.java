package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.integration.MachineAdapter;
import com.mineai.integration.MachineOperation;
import com.mineai.integration.MachineResult;
import net.minecraft.core.BlockPos;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ExtractItemTool implements AgentTool {

    @Override
    public String name() {
        return "extract_item";
    }

    @Override
    public String description() {
        return "Take items from a slot of a nearby container into the agent inventory. "
                + "Use list_container first to find a valid slot index.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("x", numberProperty("container block x"));
        properties.add("y", numberProperty("container block y"));
        properties.add("z", numberProperty("container block z"));
        properties.add("slot", numberProperty("source slot index"));
        properties.add("count", numberProperty("how many to take (default 1)"));
        return schema(name(), description(), properties, "x", "y", "z", "slot");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        MachineAdapter adapter = ContainerSupport.adapterAt(npc, pos);
        if (adapter == null) {
            return ToolResult.fail("no container within reach at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        int slot = integer(args, "slot");
        int count = args.has("count") ? Math.max(1, integer(args, "count")) : 1;

        MachineResult result = adapter.execute(npc, ContainerSupport.context(npc, pos),
                new MachineOperation.ExtractItem(slot, count));
        return result.success() ? ToolResult.ok(result.message()) : ToolResult.fail(result.message());
    }
}
