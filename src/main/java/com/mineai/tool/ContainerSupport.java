package com.mineai.tool;

import com.mineai.entity.AgentPlayer;
import com.mineai.integration.MachineAdapter;
import com.mineai.integration.MachineAdapterRegistry;
import com.mineai.integration.MachineContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class ContainerSupport {

    static final double REACH_SQR = 36.0D;

    private ContainerSupport() {
    }

    static MachineContext context(AgentPlayer npc, BlockPos pos) {
        return new MachineContext(npc.level(), pos);
    }

    static MachineAdapter adapterAt(AgentPlayer npc, BlockPos pos) {
        if (npc.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQR) {
            return null;
        }
        return MachineAdapterRegistry.find(context(npc, pos)).orElse(null);
    }
}
