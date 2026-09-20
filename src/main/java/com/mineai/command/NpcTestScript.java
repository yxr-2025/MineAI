package com.mineai.command;

import com.mineai.action.ActionQueue;
import com.mineai.action.MineAction;
import com.mineai.action.MoveAction;
import com.mineai.action.PlaceAction;
import com.mineai.entity.AgentPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Hardcoded stage 0-1 validation sequence: walk a 5x5 square, mine one block,
 * then place it back on the same spot.
 */
public final class NpcTestScript {

    private NpcTestScript() {
    }

    public static void run(AgentPlayer npc) {
        ensurePlaceableItem(npc);

        ActionQueue queue = npc.actionQueue();
        queue.clear(npc);

        BlockPos start = npc.blockPosition();
        BlockPos p1 = start.offset(5, 0, 0);
        BlockPos p2 = start.offset(5, 0, 5);
        BlockPos p3 = start.offset(0, 0, 5);

        queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p1)));
        queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p2)));
        queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p3)));
        queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(start)));

        BlockPos front = start.relative(Direction.NORTH);
        BlockPos mineTarget = npc.level().getBlockState(front).isAir() ? front.below() : front;
        queue.enqueue(new MineAction(mineTarget));
        queue.enqueue(new PlaceAction(mineTarget));
    }

    private static void ensurePlaceableItem(AgentPlayer npc) {
        if (npc.getInventory().getItem(0).isEmpty()) {
            npc.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
        }
        npc.getInventory().selected = 0;
    }
}
