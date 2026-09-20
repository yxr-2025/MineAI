package com.mineai.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class LookHelper {

    private LookHelper() {
    }

    public static void lookAt(Entity entity, Vec3 target) {
        double dx = target.x - entity.getX();
        double dy = target.y - entity.getEyeY();
        double dz = target.z - entity.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));

        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
    }
}
