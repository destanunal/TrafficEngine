package com.destan.trafficengine.network.packets.cts;

import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.block.entity.TrafficLightControllerBlockEntity;
import com.destan.trafficengine.data.TrafficLightSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class TrafficLightSchedulePacket extends NetworkPacketData {

    private static final String NBT_POS = "Pos";
    private static final String NBT_SCHEDULES = "Schedules";

    private BlockPos pos;
    private int signalFace;
    public TrafficLightSchedulePacket withFace(int face){signalFace=face==1?1:0;return this;}
    private List<TrafficLightSchedule> schedules = new ArrayList<>();

    public TrafficLightSchedulePacket(PacketStatus status) {
        super(status);
    }

    public TrafficLightSchedulePacket(BlockPos pos, List<TrafficLightSchedule> schedules) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.schedules = schedules;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
        if(signalFace!=0)nbt.putByte("SignalFace",(byte)signalFace);
        ListTag list = new ListTag();
        for (TrafficLightSchedule schedule : schedules) {
            list.add(schedule.toNbt());
        }
        nbt.put(NBT_SCHEDULES, list);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
        signalFace=nbt.getByte("SignalFace")==1?1:0;
        this.schedules = nbt.getList(NBT_SCHEDULES, Tag.TAG_COMPOUND).stream().map(x -> {
            TrafficLightSchedule schedule = new TrafficLightSchedule();
            schedule.fromNbt((CompoundTag)x);
            return schedule;
        }).toList();
    }
    
    public static void handle(TrafficLightSchedulePacket packet, NetworkPacketContext context) {
        ServerPlayer player = (ServerPlayer)context.getPlayer();
        if (player != null) {
            Level level = player.level();
            if (packet.pos!=null && level.isLoaded(packet.pos) && !player.isSpectator()
                    && player.distanceToSqr(packet.pos.getX()+.5,packet.pos.getY()+.5,packet.pos.getZ()+.5)<=64 && level.mayInteract(player,packet.pos)) {
                if (level.getBlockEntity(packet.pos) instanceof TrafficLightControllerBlockEntity blockEntity) {
                    blockEntity.setSchedules(packet.schedules);
                } else if (com.destan.trafficengine.util.SignalFaces.get(level,packet.pos,packet.signalFace) instanceof TrafficLightBlockEntity blockEntity) {
                    blockEntity.setSchedule(packet.schedules.get(0));
                }
                level.blockEntityChanged(packet.pos);
            }
        }
    }
}
