package com.destan.trafficengine.network.packets.stc;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import com.destan.trafficengine.block.entity.TrafficSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TrafficSignTextureResetPacket extends NetworkPacketData {

    private static final String NBT_POS = "Pos";

    public BlockPos pos;

    public TrafficSignTextureResetPacket(PacketStatus status) {
        super(status);
    }

    public TrafficSignTextureResetPacket(BlockPos pos) {
        super(PacketStatus.OK);
        this.pos = pos;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
    }

    public static void handle(TrafficSignTextureResetPacket packet, NetworkPacketContext context) {
        Player player = context.getPlayer();                
        Level level = player.level();
        BlockEntity entity = level.getBlockEntity(packet.pos);
        if (entity instanceof TrafficSignBlockEntity be) {
            be.resetTexture();
        }
    }
}
