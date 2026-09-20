package com.mineai.entity;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Builds the fake {@link Connection} used by an {@link AgentPlayer}.
 *
 * <p>Forge's login path calls {@code NetworkHooks.sendMCRegistryPackets}, which
 * reaches {@code NetworkFilters.injectIfNecessary} and dereferences
 * {@code Connection.channel()}. A bare {@code new Connection(...)} has a null
 * channel and throws there, so the connection is backed by a Netty
 * {@link EmbeddedChannel}. Because that pipeline has no {@code packet_handler},
 * the Forge filters skip themselves and outbound packets are simply discarded.</p>
 */
public final class NpcConnectionFactory {

    private NpcConnectionFactory() {
    }

    public static Connection create() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext context = channel.pipeline().firstContext();
        try {
            connection.channelActive(context);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to activate NPC connection", exception);
        }
        return connection;
    }
}
