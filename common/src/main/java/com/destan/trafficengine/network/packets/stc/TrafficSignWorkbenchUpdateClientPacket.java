package com.destan.trafficengine.network.packets.stc;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.client.ClientWrapper;
import net.minecraft.nbt.CompoundTag;

public class TrafficSignWorkbenchUpdateClientPacket extends NetworkPacketData {

    public TrafficSignWorkbenchUpdateClientPacket(PacketStatus status) {
        super(status);
    }
    
    public TrafficSignWorkbenchUpdateClientPacket() {
        super(PacketStatus.OK);
    }

    @Override
    protected void write(CompoundTag nbt) {
    }

    @Override
    protected void read(CompoundTag nbt) {
    }
    
    public static void handle(TrafficSignWorkbenchUpdateClientPacket packet, NetworkPacketContext context) {
        ClientWrapper.handleTrafficSignWorkbenchUpdateClientPacket(packet);
    }
}
