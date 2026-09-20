package com.mineai.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Stable external identifiers. Protocol code must use registry ids
 * ({@code minecraft:stone}) instead of translation keys.
 */
public final class RegistryIds {

    private RegistryIds() {
    }

    public static String block(BlockState state) {
        return block(state.getBlock());
    }

    public static String block(Block block) {
        return id(ForgeRegistries.BLOCKS.getKey(block));
    }

    public static String item(Item item) {
        return id(ForgeRegistries.ITEMS.getKey(item));
    }

    public static String entity(EntityType<?> type) {
        return id(ForgeRegistries.ENTITY_TYPES.getKey(type));
    }

    public static String id(ResourceLocation location) {
        return location == null ? "minecraft:air" : location.toString();
    }

    public static Item itemById(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ForgeRegistries.ITEMS.getValue(location);
    }
}
