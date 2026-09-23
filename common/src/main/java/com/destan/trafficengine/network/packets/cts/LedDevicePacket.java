package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import de.mrjulsen.mcdragonlib.data.DLStatus;
import de.mrjulsen.mcdragonlib.network.NetworkPacketContext;
import de.mrjulsen.mcdragonlib.network.NetworkPacketData;
import de.mrjulsen.mcdragonlib.util.NbtUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class LedDevicePacket extends NetworkPacketData {
    private BlockPos pos;
    private int color;
    private int interval;
    private String message;
    private long pixels;
    private boolean enabled;

    public LedDevicePacket(DLStatus status) { super(status); }
    public LedDevicePacket(BlockPos pos, int color, int interval, String message, long pixels, boolean enabled) {
        super(DLStatus.OK);
        this.pos = pos; this.color = color; this.interval = interval;
        this.message = message; this.pixels = pixels; this.enabled = enabled;
    }
    @Override protected void write(CompoundTag tag) {
        NbtUtils.putNbtPos(tag, "pos", pos); tag.putInt("color", color); tag.putInt("interval", interval);
        tag.putString("message", message); tag.putLong("pixels", pixels); tag.putBoolean("enabled", enabled);
    }
    @Override protected void read(CompoundTag tag) {
        pos = NbtUtils.getNbtBlockPos(tag, "pos"); color = tag.getInt("color"); interval = tag.getInt("interval");
        message = tag.getString("message"); pixels = tag.getLong("pixels"); enabled = tag.getBoolean("enabled");
    }
    public static void handle(LedDevicePacket packet, NetworkPacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && player.level().isLoaded(packet.pos)
                && player.level().getBlockEntity(packet.pos) instanceof LedDeviceBlockEntity blockEntity) {
            blockEntity.configure(packet.color, packet.interval, packet.message, packet.pixels, packet.enabled);
            player.level().sendBlockUpdated(packet.pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }
}
