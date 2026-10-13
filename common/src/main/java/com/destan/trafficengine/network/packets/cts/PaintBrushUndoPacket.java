package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.item.RoadPaintUndo;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public class PaintBrushUndoPacket extends NetworkPacketData {
    private boolean offhand;
    public PaintBrushUndoPacket(PacketStatus status) { super(status); }
    public PaintBrushUndoPacket(InteractionHand hand) { super(PacketStatus.OK); offhand = hand == InteractionHand.OFF_HAND; }
    @Override protected void write(CompoundTag tag) { tag.putBoolean("offhand", offhand); }
    @Override protected void read(CompoundTag tag) { offhand = tag.getBoolean("offhand"); }

    public static void handle(PaintBrushUndoPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        var brush = player.getItemInHand(packet.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (brush.getItem() instanceof BrushItem) RoadPaintUndo.undo(player, brush);
    }
}
