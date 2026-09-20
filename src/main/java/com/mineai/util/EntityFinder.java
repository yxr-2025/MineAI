package com.mineai.util;

import com.mineai.entity.AgentPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class EntityFinder {

    private EntityFinder() {
    }

    /**
     * Nearest living entity of the given registry type within range. A blank or
     * {@code any} type matches every non-player living entity.
     */
    public static LivingEntity nearest(AgentPlayer npc, String typeId, double range) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean any = typeId == null || typeId.isBlank() || "any".equalsIgnoreCase(typeId);

        for (Entity entity : npc.level().getEntities(npc, npc.getBoundingBox().inflate(range))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            if (living instanceof Player) {
                continue;
            }
            if (!any && !RegistryIds.entity(living.getType()).equals(typeId)) {
                continue;
            }
            double distance = npc.distanceToSqr(living);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }
}
