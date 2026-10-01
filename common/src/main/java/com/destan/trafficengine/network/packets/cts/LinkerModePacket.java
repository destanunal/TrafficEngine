package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.item.TrafficLightLinkerItem;
import com.destan.trafficengine.item.TrafficLightLinkerItem.LinkerMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class LinkerModePacket extends NetworkPacketData {

    private static final String NBT_DATA = "Data";

    private LinkerMode mode;

    public LinkerModePacket(PacketStatus status) {
        super(status);
    }

    public LinkerModePacket(LinkerMode mode) {
        super(PacketStatus.OK);
        this.mode = mode;
    }

    @Override
    protected void write(CompoundTag nbt) {
        nbt.putInt(NBT_DATA, mode.getIndex());
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.mode = LinkerMode.getByIndex(nbt.getInt(NBT_DATA));
    }
    
    public static void handle(LinkerModePacket packet, NetworkPacketContext context) {
        ServerPlayer sender = (ServerPlayer)context.getPlayer();
        if (sender.getMainHandItem().getItem() instanceof TrafficLightLinkerItem) {
            TrafficLightLinkerItem.setMode(sender.getMainHandItem(), packet.mode);
        } else if (sender.getOffhandItem().getItem() instanceof TrafficLightLinkerItem) { 
            TrafficLightLinkerItem.setMode(sender.getOffhandItem(), packet.mode);
        }
        sender.getInventory().setChanged();
    }
}
