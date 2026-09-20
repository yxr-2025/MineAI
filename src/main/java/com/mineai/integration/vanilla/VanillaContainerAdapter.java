package com.mineai.integration.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.integration.MachineAdapter;
import com.mineai.integration.MachineContext;
import com.mineai.integration.MachineDescription;
import com.mineai.integration.MachineOperation;
import com.mineai.integration.MachineResult;
import com.mineai.integration.SlotInfo;
import com.mineai.util.RegistryIds;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for any block whose block entity is a vanilla {@link Container}
 * (chests, barrels, furnaces, hoppers, ...). This is the foundation every
 * other machine adapter builds on.
 */
public final class VanillaContainerAdapter implements MachineAdapter {

    @Override
    public boolean supports(MachineContext context) {
        return context.level().getBlockEntity(context.pos()) instanceof Container;
    }

    @Override
    public MachineDescription describe(MachineContext context) {
        Container container = containerAt(context);
        if (container == null) {
            return new MachineDescription("minecraft:container", "Container", List.of(), List.of());
        }

        List<SlotInfo> slots = new ArrayList<>();
        for (int index = 0; index < container.getContainerSize(); index++) {
            slots.add(new SlotInfo(index, "slot_" + index, "item", -1, -1));
        }
        return new MachineDescription(
                "minecraft:container",
                "Container",
                slots,
                List.of("query", "insert", "extract"));
    }

    @Override
    public MachineResult execute(AgentPlayer npc, MachineContext context, MachineOperation operation) {
        Container container = containerAt(context);
        if (container == null) {
            return MachineResult.fail("no container at " + context.pos());
        }

        if (operation instanceof MachineOperation.QueryContents) {
            return query(container);
        }
        if (operation instanceof MachineOperation.InsertItem insert) {
            return insert(container, insert);
        }
        if (operation instanceof MachineOperation.ExtractItem extract) {
            return extract(npc, container, extract);
        }
        return MachineResult.fail("unsupported operation");
    }

    private MachineResult query(Container container) {
        JsonArray slots = new JsonArray();
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", index);
            entry.addProperty("item", stack.isEmpty() ? "minecraft:air" : RegistryIds.item(stack.getItem()));
            entry.addProperty("count", stack.getCount());
            slots.add(entry);
        }
        JsonObject data = new JsonObject();
        data.add("slots", slots);
        return MachineResult.ok("queried container", data);
    }

    private MachineResult insert(Container container, MachineOperation.InsertItem insert) {
        int slot = insert.slot();
        if (slot < 0 || slot >= container.getContainerSize()) {
            return MachineResult.fail("slot out of range: " + slot);
        }
        ItemStack incoming = insert.stack();
        if (incoming.isEmpty()) {
            return MachineResult.fail("nothing to insert");
        }

        ItemStack existing = container.getItem(slot);
        int max = Math.min(container.getMaxStackSize(), incoming.getMaxStackSize());

        if (existing.isEmpty()) {
            if (!container.canPlaceItem(slot, incoming)) {
                return MachineResult.fail("slot " + slot + " rejects item");
            }
            if (incoming.getCount() > max) {
                return MachineResult.fail("slot " + slot + " cannot hold " + incoming.getCount());
            }
            container.setItem(slot, incoming.copy());
            container.setChanged();
            return MachineResult.ok("inserted " + incoming.getCount() + " into slot " + slot);
        }

        if (!ItemStack.isSameItemSameTags(existing, incoming)) {
            return MachineResult.fail("slot " + slot + " holds a different item");
        }
        if (existing.getCount() + incoming.getCount() > max) {
            return MachineResult.fail("slot " + slot + " does not have enough room");
        }
        existing.grow(incoming.getCount());
        container.setChanged();
        return MachineResult.ok("merged " + incoming.getCount() + " into slot " + slot);
    }

    private MachineResult extract(AgentPlayer npc, Container container, MachineOperation.ExtractItem extract) {
        int slot = extract.slot();
        if (slot < 0 || slot >= container.getContainerSize()) {
            return MachineResult.fail("slot out of range: " + slot);
        }
        int amount = Math.max(1, extract.amount());
        ItemStack taken = container.removeItem(slot, amount);
        if (taken.isEmpty()) {
            return MachineResult.fail("slot " + slot + " is empty");
        }
        if (!npc.getInventory().add(taken)) {
            container.setItem(slot, taken);
            return MachineResult.fail("inventory full");
        }
        container.setChanged();
        return MachineResult.ok("extracted " + taken.getCount() + " " + RegistryIds.item(taken.getItem()));
    }

    private Container containerAt(MachineContext context) {
        if (context.level().getBlockEntity(context.pos()) instanceof Container container) {
            return container;
        }
        return null;
    }
}
