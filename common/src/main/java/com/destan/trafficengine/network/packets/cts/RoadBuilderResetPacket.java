package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.item.RoadConstructionTool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class RoadBuilderResetPacket extends NetworkPacketData {
    
    public RoadBuilderResetPacket(PacketStatus status) {
        super(status);
    }

    public RoadBuilderResetPacket() {
        super(PacketStatus.OK);
    }

    @Override
    protected void write(CompoundTag nbt) {
    }

    @Override
    protected void read(CompoundTag nbt) {
    }
    
    public static void handle(RoadBuilderResetPacket packet, NetworkPacketContext context) {        
        ServerPlayer sender = (ServerPlayer)context.getPlayer();
        if (sender.getMainHandItem().getItem() instanceof RoadConstructionTool) {
            RoadConstructionTool.reset(sender.getMainHandItem());
        } else if (sender.getOffhandItem().getItem() instanceof RoadConstructionTool) {
            RoadConstructionTool.reset(sender.getOffhandItem());
        }
        sender.getInventory().setChanged();
    }
}
