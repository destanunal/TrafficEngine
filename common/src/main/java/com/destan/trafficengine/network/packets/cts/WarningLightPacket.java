package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.WarningLightBlock;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.data.FlashSequence;
import com.destan.trafficengine.data.WarningLightSettings;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class WarningLightPacket extends NetworkPacketData {
    private BlockPos pos;
    private WarningLightSettings settings;
    private int lamps;
    private boolean horizontal, valid;
    public WarningLightPacket(PacketStatus status) { super(status); }
    public WarningLightPacket(BlockPos pos, WarningLightSettings settings, int lamps, boolean horizontal) {
        super(PacketStatus.OK); this.pos = pos; this.settings = settings; this.lamps = lamps; this.horizontal = horizontal; valid = true;
    }
    @Override protected void write(CompoundTag tag) {
        tag.putLong("pos", pos.asLong()); tag.putInt("lamps", lamps); tag.putBoolean("horizontal", horizontal); settings.write(tag);
    }
    @Override protected void read(CompoundTag tag) {
        pos = BlockPos.of(tag.getLong("pos")); lamps = tag.getInt("lamps") == 1 ? 1 : 2;
        horizontal = tag.getBoolean("horizontal"); valid = FlashSequence.valid(tag.getString("flash_steps")); settings = WarningLightSettings.read(tag);
    }
    public static void handle(WarningLightPacket packet, NetworkPacketContext context) {
        if (!packet.valid || !(context.getPlayer() instanceof ServerPlayer player) || player.isSpectator()) return;
        var level = player.level();
        if (packet.pos == null || !level.isLoaded(packet.pos)
                || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 64
                || !level.mayInteract(player, packet.pos) || !player.mayUseItemAt(packet.pos, Direction.UP, player.getMainHandItem())) return;
        var state = level.getBlockState(packet.pos);
        if (!(state.getBlock() instanceof WarningLightBlock)) return;
        var updated = state.setValue(TrafficLightBlock.MODEL, packet.lamps == 1 ? TrafficLightModel.ONE_LIGHT : TrafficLightModel.TWO_LIGHTS)
            .setValue(WarningLightBlock.HORIZONTAL, packet.horizontal).setValue(TrafficLightBlock.DIAGONAL, false);
        if (!updated.equals(state)) level.setBlockAndUpdate(packet.pos, updated);
        if (level.getBlockEntity(packet.pos) instanceof TrafficLightBlockEntity lamp) {
            var settings = packet.settings;
            lamp.configureWarning(settings.primary(), settings.secondary(), settings.sequence(), settings.redstone());
        }
    }
}
