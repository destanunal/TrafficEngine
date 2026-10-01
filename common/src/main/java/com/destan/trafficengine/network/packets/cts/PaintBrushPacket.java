package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.item.BrushItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PaintBrushPacket extends NetworkPacketData {

    private static final String NBT_DATA = "Data";

    private int pattern;

    public PaintBrushPacket(PacketStatus status) {
        super(status);
    }

    public PaintBrushPacket(int pattern) {
        super(PacketStatus.OK);
        this.pattern = pattern;
    }

    @Override
    protected void write(CompoundTag nbt) {
        nbt.putInt(NBT_DATA, pattern);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pattern = nbt.getInt(NBT_DATA);
    }
    
    public static void handle(PaintBrushPacket packet, NetworkPacketContext context) {
        ServerPlayer sender = (ServerPlayer)context.getPlayer();

        if(sender.getMainHandItem().getItem() instanceof BrushItem) {
            CompoundTag nbt = sender.getMainHandItem().getTag();
            nbt.putInt(BrushItem.NBT_PATTERN, packet.pattern);
        } else if (sender.getOffhandItem().getItem() instanceof BrushItem) {
            
            CompoundTag nbt = sender.getOffhandItem().getTag();
            nbt.putInt(BrushItem.NBT_PATTERN, packet.pattern);
        }
        sender.getInventory().setChanged();
    }
}
