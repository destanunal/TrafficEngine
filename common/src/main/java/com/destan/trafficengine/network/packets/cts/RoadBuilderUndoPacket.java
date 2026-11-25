package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.item.RoadConstructionUndo;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class RoadBuilderUndoPacket extends NetworkPacketData {
    public RoadBuilderUndoPacket(PacketStatus status) {
        super(status);
    }

    public RoadBuilderUndoPacket() {
        super(PacketStatus.OK);
    }

    @Override protected void write(CompoundTag tag) {}
    @Override protected void read(CompoundTag tag) {}

    public static void handle(RoadBuilderUndoPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        var tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof RoadConstructionTool)) tool = player.getOffhandItem();
        if (tool.getItem() instanceof RoadConstructionTool) RoadConstructionUndo.undo(player, tool);
    }
}
