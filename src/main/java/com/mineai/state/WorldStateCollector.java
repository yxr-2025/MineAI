package com.mineai.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Read-only snapshot of the world around an NPC. The JSON field names are a
 * stable protocol and use snake_case.
 */
public final class WorldStateCollector {

    private static final int HORIZONTAL_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 4;
    private static final double ENTITY_RADIUS = 16.0D;

    private WorldStateCollector() {
    }

    public static JsonObject collect(AgentPlayer npc) {
        JsonObject root = new JsonObject();
        root.add("self", collectSelf(npc));
        root.add("inventory", collectInventory(npc));
        root.add("nearby_blocks", collectBlocks(npc));
        root.add("nearby_entities", collectEntities(npc));
        root.add("environment", collectEnvironment(npc));
        return root;
    }

    private static JsonObject collectSelf(AgentPlayer npc) {
        JsonObject self = new JsonObject();
        self.addProperty("x", npc.getX());
        self.addProperty("y", npc.getY());
        self.addProperty("z", npc.getZ());
        self.addProperty("health", npc.getHealth());
        self.addProperty("food", npc.getFoodData().getFoodLevel());
        self.addProperty("yaw", npc.getYRot());
        self.addProperty("pitch", npc.getXRot());
        self.addProperty("dimension", npc.level().dimension().location().toString());
        self.addProperty("air", npc.getAirSupply());
        self.addProperty("max_air", npc.getMaxAirSupply());
        self.addProperty("attack_charge", npc.getAttackStrengthScale(1.0F));
        self.addProperty("attack_cooldown_ticks", npc.getCurrentItemAttackStrengthDelay());
        return self;
    }

    private static JsonArray collectInventory(AgentPlayer npc) {
        JsonArray inventory = new JsonArray();
        int size = npc.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = npc.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            entry.addProperty("item", RegistryIds.item(stack.getItem()));
            entry.addProperty("count", stack.getCount());
            inventory.add(entry);
        }
        return inventory;
    }

    private static JsonArray collectBlocks(AgentPlayer npc) {
        JsonArray blocks = new JsonArray();
        Level level = npc.level();
        BlockPos center = npc.blockPosition();

        addBlockEntry(blocks, level, center, "self_feet");
        addBlockEntry(blocks, level, center.above(), "self_head");
        addBlockEntry(blocks, level, center.below(), "self_support");
        addBlockEntry(blocks, level, center.relative(npc.getDirection()), "self_front");

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-HORIZONTAL_RADIUS, -VERTICAL_RADIUS, -HORIZONTAL_RADIUS),
                center.offset(HORIZONTAL_RADIUS, VERTICAL_RADIUS, HORIZONTAL_RADIUS))) {
            if (!isInteresting(level.getBlockState(pos))) {
                continue;
            }
            addBlockEntry(blocks, level, pos.immutable(), null);
        }
        return blocks;
    }

    private static void addBlockEntry(JsonArray target, Level level, BlockPos pos, String role) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("x", pos.getX());
        entry.addProperty("y", pos.getY());
        entry.addProperty("z", pos.getZ());
        entry.addProperty("block", RegistryIds.block(state));
        if (role != null) {
            entry.addProperty("role", role);
        }
        target.add(entry);
    }

    private static JsonArray collectEntities(AgentPlayer npc) {
        JsonArray entities = new JsonArray();
        for (Entity entity : npc.level().getEntities(npc, npc.getBoundingBox().inflate(ENTITY_RADIUS))) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", entity.getId());
            entry.addProperty("type", RegistryIds.entity(entity.getType()));
            entry.addProperty("x", entity.getX());
            entry.addProperty("y", entity.getY());
            entry.addProperty("z", entity.getZ());
            entry.addProperty("distance", Math.sqrt(npc.distanceToSqr(entity)));
            if (entity instanceof LivingEntity living) {
                entry.addProperty("health", living.getHealth());
            }
            if (entity instanceof net.minecraft.world.entity.npc.Villager villager) {
                entry.addProperty("profession", villager.getVillagerData().getProfession().toString());
                entry.addProperty("trade_level", villager.getVillagerData().getLevel());
                entry.addProperty("offers", villager.getOffers().size());
            }
            entities.add(entry);
        }
        return entities;
    }

    private static JsonObject collectEnvironment(AgentPlayer npc) {
        Level level = npc.level();
        JsonObject environment = new JsonObject();
        environment.addProperty("time", level.getDayTime() % 24000L);
        environment.addProperty("is_raining", level.isRaining());
        environment.addProperty("dimension", level.dimension().location().toString());
        return environment;
    }

    private static boolean isInteresting(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FurnaceBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock;
    }
}
