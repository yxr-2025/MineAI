package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.integration.MachineAdapter;
import com.mineai.integration.MachineOperation;
import com.mineai.integration.MachineResult;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class InsertItemTool implements AgentTool {

    @Override
    public String name() {
        return "insert_item";
    }

    @Override
    public String description() {
        return "Move an item from the agent inventory into a slot of a nearby container. "
                + "Use list_container first to find a valid slot index.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("x", numberProperty("container block x"));
        properties.add("y", numberProperty("container block y"));
        properties.add("z", numberProperty("container block z"));
        properties.add("slot", numberProperty("target slot index"));
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id, for example minecraft:torch");
        properties.add("item", item);
        properties.add("count", numberProperty("how many to move (default 1)"));
        return schema(name(), description(), properties, "x", "y", "z", "slot", "item");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        MachineAdapter adapter = ContainerSupport.adapterAt(npc, pos);
        if (adapter == null) {
            return ToolResult.fail("no container within reach at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        int slot = integer(args, "slot");
        String itemId = args.get("item").getAsString();
        Item item = RegistryIds.itemById(itemId);
        if (item == null) {
            return ToolResult.fail("unknown item: " + itemId);
        }

        ItemStack source = InventoryHelper.find(npc, item);
        if (source.isEmpty()) {
            return ToolResult.fail("inventory has no " + itemId);
        }

        int amount = args.has("count") ? Math.max(1, integer(args, "count")) : 1;
        amount = Math.min(amount, source.getCount());
        ItemStack toInsert = source.split(amount);

        MachineResult result = adapter.execute(npc, ContainerSupport.context(npc, pos),
                new MachineOperation.InsertItem(slot, toInsert));
        if (!result.success()) {
            npc.getInventory().add(toInsert);
            return ToolResult.fail(result.message());
        }
        return ToolResult.ok(result.message());
    }
}
