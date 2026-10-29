package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.data.PaintableRailing;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** An explicit brush erase does not enter vanilla's block-breaking path. */
public final class RailingPaintErasePacket extends NetworkPacketData {
    private BlockPos pos=BlockPos.ZERO;
    public RailingPaintErasePacket(PacketStatus status) { super(status); }
    public RailingPaintErasePacket(BlockPos pos) { super(PacketStatus.OK); this.pos=pos.immutable(); }
    @Override protected void write(CompoundTag tag) { tag.putLong("pos",pos.asLong()); }
    @Override protected void read(CompoundTag tag) { pos=BlockPos.of(tag.getLong("pos")); }
    public static void handle(RailingPaintErasePacket packet,NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || player.isSpectator() || !player.mayBuild()
                || !(player.getMainHandItem().getItem() instanceof BrushItem)) return;
        var world=player.serverLevel(); BlockPos pos=packet.pos;
        double reach=player.isCreative()?6:5;
        if (player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>reach*reach
                || !world.hasChunkAt(pos) || !world.mayInteract(player,pos)) return;
        var state=world.getBlockState(pos);
        if (state.getBlock() instanceof PaintableRailing railing) railing.onRemoveColor(state,world,pos,player);
    }
}
