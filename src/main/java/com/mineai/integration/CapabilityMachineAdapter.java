package com.mineai.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability-first adapter for modded machines.
 *
 * <p>Any block entity that exposes a Forge {@code IItemHandler} is usable, so
 * Mekanism, Thermal and most other tech mods work without mod-specific code.
 * The slot semantics come from the capability itself, not from GUI coordinates
 * or class names.</p>
 */
public final class CapabilityMachineAdapter implements MachineAdapter {

    @Override
    public boolean supports(MachineContext context) {
        return handler(context) != null;
    }

    @Override
    public MachineDescription describe(MachineContext context) {
        IItemHandler handler = handler(context);
        if (handler == null) {
            return new MachineDescription("forge:item_handler", "Machine", List.of(), List.of());
        }
        List<SlotInfo> slots = new ArrayList<>();
        for (int index = 0; index < handler.getSlots(); index++) {
            slots.add(new SlotInfo(index, "slot_" + index, "item", -1, -1));
        }
        return new MachineDescription("forge:item_handler", "Machine", slots,
                List.of("query", "insert", "extract"));
    }

    @Override
    public MachineResult execute(AgentPlayer npc, MachineContext context, MachineOperation operation) {
        IItemHandler handler = handler(context);
        if (handler == null) {
            return MachineResult.fail("no item handler at " + context.pos());
        }
        if (operation instanceof MachineOperation.QueryContents) {
            return query(handler);
        }
        if (operation instanceof MachineOperation.InsertItem insert) {
            return insert(npc, handler, insert);
        }
        if (operation instanceof MachineOperation.ExtractItem extract) {
            return extract(npc, handler, extract);
        }
        return MachineResult.fail("unsupported operation");
    }

    private MachineResult query(IItemHandler handler) {
        JsonArray slots = new JsonArray();
        for (int index = 0; index < handler.getSlots(); index++) {
            ItemStack stack = handler.getStackInSlot(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", index);
            entry.addProperty("item", stack.isEmpty() ? "minecraft:air" : RegistryIds.item(stack.getItem()));
            entry.addProperty("count", stack.getCount());
            slots.add(entry);
        }
        JsonObject data = new JsonObject();
        data.add("slots", slots);
        return MachineResult.ok("queried machine", data);
    }

    private MachineResult insert(AgentPlayer npc, IItemHandler handler, MachineOperation.InsertItem insert) {
        int slot = insert.slot();
        if (slot < 0 || slot >= handler.getSlots()) {
            return MachineResult.fail("slot out of range: " + slot);
        }
        ItemStack incoming = insert.stack();
        if (incoming.isEmpty()) {
            return MachineResult.fail("nothing to insert");
        }
        ItemStack remainder = handler.insertItem(slot, incoming, false);
        if (remainder.getCount() == incoming.getCount()) {
            return MachineResult.fail("slot " + slot + " rejected the item");
        }
        int moved = incoming.getCount() - remainder.getCount();
        if (!remainder.isEmpty() && !npc.getInventory().add(remainder)) {
            npc.drop(remainder, false);
        }
        return MachineResult.ok("inserted " + moved + " into slot " + slot);
    }

    private MachineResult extract(AgentPlayer npc, IItemHandler handler, MachineOperation.ExtractItem extract) {
        int slot = extract.slot();
        if (slot < 0 || slot >= handler.getSlots()) {
            return MachineResult.fail("slot out of range: " + slot);
        }
        int amount = Math.max(1, extract.amount());
        ItemStack taken = handler.extractItem(slot, amount, false);
        if (taken.isEmpty()) {
            return MachineResult.fail("slot " + slot + " is empty");
        }
        if (!npc.getInventory().add(taken)) {
            npc.drop(taken, false);
        }
        return MachineResult.ok("extracted " + taken.getCount() + " " + RegistryIds.item(taken.getItem()));
    }

    private static IItemHandler handler(MachineContext context) {
        if (context.level().getBlockEntity(context.pos()) == null) {
            return null;
        }
        return context.level().getBlockEntity(context.pos())
                .getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .orElse(null);
    }
}
