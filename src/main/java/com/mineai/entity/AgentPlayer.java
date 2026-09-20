package com.mineai.entity;

import com.mineai.action.ActionQueue;
import com.mineai.agent.AgentController;
import com.mineai.movement.NpcMover;
import com.mineai.movement.PathMover;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A server-side player entity that looks and behaves like a real player,
 * but whose "brain" is driven by the mod instead of a network client.
 */
public class AgentPlayer extends ServerPlayer {

    private final String npcId;
    private final Connection npcConnection;
    private final NpcMover mover;
    private final ActionQueue actionQueue;
    private AgentController agentController;

    public AgentPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, String npcId) {
        super(server, level, profile);
        this.npcId = npcId;
        this.npcConnection = NpcConnectionFactory.create();
        this.connection = new NpcGamePacketListener(server, this, this.npcConnection);
        this.mover = new PathMover();
        this.actionQueue = new ActionQueue();
    }

    public String npcId() {
        return npcId;
    }

    public Connection npcConnection() {
        return npcConnection;
    }

    public NpcMover mover() {
        return mover;
    }

    public ActionQueue actionQueue() {
        return actionQueue;
    }

    public AgentController agentController() {
        if (agentController == null) {
            agentController = new AgentController(this);
        }
        return agentController;
    }

    public AgentController agentControllerOrNull() {
        return agentController;
    }

    /**
     * Fake players do not reliably run the vanilla item-pickup scan, so the
     * nearby item entities are touched explicitly each tick.
     */
    private void collectNearbyItems() {
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1.5D, 1.5D, 1.5D))) {
            if (entity instanceof ItemEntity item && !item.isRemoved()) {
                item.playerTouch(this);
            }
        }
    }

    /**
     * Called once per server tick by {@link AgentPlayerManager}.
     */
    public void serverTick() {
        if (this.isRemoved()) {
            return;
        }
        collectNearbyItems();
        if (agentController != null) {
            agentController.tick();
        }
        mover.tick(this);
        actionQueue.tick(this);
    }

    @Override
    public String toString() {
        return "AgentPlayer[" + npcId + "," + getUUID() + "]";
    }
}
