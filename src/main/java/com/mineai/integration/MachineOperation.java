package com.mineai.integration;

import net.minecraft.world.item.ItemStack;

/**
 * Structured, bounded operation set. Adapters never receive free-form text.
 */
public sealed interface MachineOperation {

    record QueryContents() implements MachineOperation {
    }

    record InsertItem(int slot, ItemStack stack) implements MachineOperation {
    }

    record ExtractItem(int slot, int amount) implements MachineOperation {
    }
}
