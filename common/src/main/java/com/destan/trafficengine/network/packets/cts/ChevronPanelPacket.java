package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.ChevronPanelBlock;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class ChevronPanelPacket extends NetworkPacketData {
    private BlockPos pos;
    private boolean right, redWhite, single;
    public ChevronPanelPacket(PacketStatus status) { super(status); }
    public ChevronPanelPacket(BlockPos pos, boolean right, boolean redWhite, boolean single) { super(PacketStatus.OK); this.pos = pos; this.right = right; this.redWhite = redWhite; this.single = single; }
    @Override protected void write(CompoundTag tag) { tag.putLong("pos", pos.asLong()); tag.putBoolean("right", right); tag.putBoolean("red_white", redWhite); tag.putBoolean("single", single); }
    @Override protected void read(CompoundTag tag) { pos = BlockPos.of(tag.getLong("pos")); right = tag.getBoolean("right"); redWhite = tag.getBoolean("red_white"); single = tag.getBoolean("single"); }
    public static void handle(ChevronPanelPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || player.isSpectator()) return;
        var level = player.level();
        if (packet.pos == null || !level.isLoaded(packet.pos)
                || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 64
                || !level.mayInteract(player, packet.pos) || !player.mayUseItemAt(packet.pos, Direction.UP, player.getMainHandItem())) return;
        var state = level.getBlockState(packet.pos);
        if (!(state.getBlock() instanceof ChevronPanelBlock)) return;
        var updated = ChevronPanelBlock.withSingle(state, packet.single, level, packet.pos).setValue(ChevronPanelBlock.ARROW_RIGHT, packet.right).setValue(ChevronPanelBlock.RED_WHITE, packet.redWhite);
        if (updated != state) level.setBlockAndUpdate(packet.pos, updated);
    }
}
