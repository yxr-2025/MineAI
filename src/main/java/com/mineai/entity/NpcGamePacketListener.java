package com.mineai.entity;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Fake connection listener for an AgentPlayer.
 *
 * <p>An AgentPlayer has no real client, so every outbound packet is dropped.
 * The server still broadcasts the NPC to real players through the normal
 * entity tracker and player-info channels.</p>
 */
public final class NpcGamePacketListener extends ServerGamePacketListenerImpl {

    public NpcGamePacketListener(MinecraftServer server, ServerPlayer player, Connection connection) {
        super(server, connection, player);
    }

    @Override
    public void send(Packet<?> packet) {
        // No real client to receive this packet.
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        // No real client to receive this packet.
    }
}
