package com.mineai.movement;

import com.mineai.entity.AgentPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-driven movement for an {@link AgentPlayer}.
 *
 * <p>Vanilla {@code PathNavigation} belongs to {@code Mob}, so a
 * {@code ServerPlayer} cannot use it. Every mover implementation therefore
 * owns its own pathing and collision handling.</p>
 */
public interface NpcMover {

    void moveTo(AgentPlayer npc, Vec3 target, double speed);

    void stop();

    void tick(AgentPlayer npc);

    boolean isDone();

    boolean isFailed();

    MoveState state();

    Vec3 target();
}
