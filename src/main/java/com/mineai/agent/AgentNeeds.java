package com.mineai.agent;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

/**
 * Snapshot of the agent's drives. These are inputs for the model, not a
 * decision: the model reads them and chooses what to do.
 */
public final class AgentNeeds {

    private AgentNeeds() {
    }

    public static JsonObject snapshot(AgentPlayer npc) {
        JsonObject needs = new JsonObject();

        needs.addProperty("food", npc.getFoodData().getFoodLevel());
        needs.addProperty("health", npc.getHealth());
        needs.addProperty("max_health", npc.getMaxHealth());
        needs.addProperty("air", npc.getAirSupply());

        int hostiles = 0;
        double nearestHostile = -1.0D;
        int droppedItems = 0;
        int villagers = 0;
        for (Entity entity : npc.level().getEntities(npc, npc.getBoundingBox().inflate(24.0D))) {
            if (entity instanceof Enemy) {
                hostiles++;
                double distance = npc.distanceTo(entity);
                if (nearestHostile < 0 || distance < nearestHostile) {
                    nearestHostile = distance;
                }
            } else if (entity instanceof ItemEntity) {
                droppedItems++;
            } else if (entity instanceof net.minecraft.world.entity.npc.Villager) {
                villagers++;
            }
        }
        needs.addProperty("hostiles_nearby", hostiles);
        needs.addProperty("nearest_hostile_distance", nearestHostile);
        needs.addProperty("dropped_items_nearby", droppedItems);
        needs.addProperty("villagers_nearby", villagers);

        needs.addProperty("has_food", hasFood(npc));
        needs.addProperty("has_tool", hasTool(npc));
        needs.addProperty("empty_handed", npc.getMainHandItem().isEmpty());
        needs.addProperty("free_slots", freeSlots(npc));

        needs.addProperty("is_night", !npc.level().isDay());
        needs.addProperty("is_raining", npc.level().isRaining());
        needs.addProperty("dimension", npc.level().dimension().location().toString());
        return needs;
    }

    private static boolean hasFood(AgentPlayer npc) {
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            ItemStack stack = npc.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.isEdible()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTool(AgentPlayer npc) {
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            ItemStack stack = npc.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof net.minecraft.world.item.DiggerItem) {
                return true;
            }
        }
        return false;
    }

    private static int freeSlots(AgentPlayer npc) {
        int free = 0;
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            if (npc.getInventory().getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }
}
