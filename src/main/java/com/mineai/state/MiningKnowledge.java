package com.mineai.state;

import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a block requires and what it drops.
 *
 * <p>Tool requirements come from vanilla tags ({@code NEEDS_*_TOOL},
 * {@code MINEABLE_WITH_*}), and drops come from the real loot table, so this
 * works for modded blocks too.</p>
 */
public final class MiningKnowledge {

    public enum ToolKind {
        NONE, PICKAXE, AXE, SHOVEL, HOE, SHEARS, OTHER
    }

    public enum Tier {
        HAND, WOOD, STONE, IRON, DIAMOND, NETHERITE
    }

    private MiningKnowledge() {
    }

    public static ToolKind toolKind(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return ToolKind.PICKAXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return ToolKind.AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return ToolKind.SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return ToolKind.HOE;
        }
        return ToolKind.NONE;
    }

    public static Tier requiredTier(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return Tier.DIAMOND;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return Tier.IRON;
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return Tier.STONE;
        }
        return Tier.HAND;
    }

    /**
     * True when the held stack can actually harvest the block (vanilla check).
     */
    public static boolean canHarvest(ItemStack tool, BlockState state) {
        if (requiredTier(state) == Tier.HAND) {
            return true;
        }
        return !tool.isEmpty() && tool.isCorrectToolForDrops(state);
    }

    /**
     * First inventory stack that can harvest the block, or empty.
     */
    public static ItemStack findToolFor(net.minecraft.world.entity.player.Player player, BlockState state) {
        if (requiredTier(state) == Tier.HAND) {
            return ItemStack.EMPTY;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The item id a block drops, using the real loot table.
     */
    public static String expectedDrop(Level level, BlockPos pos, BlockState state, Entity breaker, ItemStack tool) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, breaker, tool);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                return RegistryIds.item(drop.getItem());
            }
        }
        return null;
    }

    public static String describeRequirement(BlockState state) {
        ToolKind kind = toolKind(state);
        Tier tier = requiredTier(state);
        if (kind == ToolKind.NONE) {
            return "no tool needed";
        }
        return "needs " + tier.name().toLowerCase() + " " + kind.name().toLowerCase();
    }

    /**
     * Positions of blocks near the agent that drop the given item. Used by the
     * gather process so it does not have to search blindly.
     */
    public static List<BlockPos> findSources(Level level, BlockPos center, int radius, int vertical,
                                             Entity breaker, ItemStack tool, String itemId, int limit) {
        List<BlockPos> exposed = new ArrayList<>();
        List<BlockPos> buried = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -vertical, -radius),
                center.offset(radius, vertical, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            String drop = expectedDrop(level, pos, state, breaker, tool);
            if (!itemId.equals(drop)) {
                continue;
            }
            // Prefer blocks the agent can reach without digging: air above them.
            if (level.getBlockState(pos.above()).isAir()) {
                exposed.add(pos.immutable());
            } else {
                buried.add(pos.immutable());
            }
        }
        exposed.sort(java.util.Comparator.comparingDouble(center::distSqr));
        buried.sort(java.util.Comparator.comparingDouble(center::distSqr));

        List<BlockPos> result = new ArrayList<>(exposed);
        result.addAll(buried);
        return result.size() > limit ? result.subList(0, limit) : result;
    }
}
