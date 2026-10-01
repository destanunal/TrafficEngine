package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import com.destan.trafficengine.block.entity.TrafficLightControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class TrafficLightControllerPacket extends NetworkPacketData {

    private static final String NBT_POS = "Pos";
    private static final String NBT_STATUS = "Status";

    private BlockPos pos;
    private boolean status;

    public TrafficLightControllerPacket(PacketStatus status) {
        super(status);
    }

    public TrafficLightControllerPacket(BlockPos pos, boolean status) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.status = status;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
        nbt.putBoolean(NBT_STATUS, status);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
        this.status = nbt.getBoolean(NBT_STATUS);
    }

    public static void handle(TrafficLightControllerPacket packet, NetworkPacketContext context) {        
        ServerPlayer player = (ServerPlayer)context.getPlayer();
        if (player != null) {
            Level level = player.level();
            if (level.isLoaded(packet.pos)) {
                if (level.getBlockEntity(packet.pos) instanceof TrafficLightControllerBlockEntity blockEntity) {
                    blockEntity.setRunning(packet.status);
                }
            }
        }
    }
}
