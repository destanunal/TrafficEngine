package com.destan.trafficengine.client;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Client-only buffer creation, kept out of dedicated-server initialization. */
public final class ClientPacketBuffers {
    private ClientPacketBuffers() {}

    public static RegistryFriendlyByteBuf create() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) throw new IllegalStateException("No active connection");
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
    }
}
