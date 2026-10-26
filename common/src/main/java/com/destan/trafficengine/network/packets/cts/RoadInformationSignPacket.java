package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import com.destan.trafficengine.data.RoadSignDesign;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.registry.ModItemTags;
import com.destan.trafficengine.util.NbtPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class RoadInformationSignPacket extends NetworkPacketData {
    private BlockPos pos;
    private String[] lines;
    private String[] distances;
    private int[] arrows;
    private String route;
    private int theme, speed;
    private boolean enabled;
    private RoadSignDesign design;
    public RoadInformationSignPacket(PacketStatus status) { super(status); }
    public RoadInformationSignPacket(BlockPos pos, String[] lines, int[] arrows, String[] distances, String route,
            int theme, int speed, boolean enabled, RoadSignDesign design) {
        super(PacketStatus.OK); this.pos = pos; this.lines = lines.clone(); this.arrows = arrows.clone();
        this.route = route; this.theme = theme; this.speed = speed; this.enabled = enabled;
        this.design = design; this.distances = distances.clone();
    }
    @Override protected void write(CompoundTag tag) {
        NbtPositions.putNbtPos(tag, "pos", pos);
        for (int i = 0; i < 4; i++) {
            tag.putString("line" + i, i < lines.length ? lines[i] : "");
            tag.putString("distance" + i, i < distances.length ? distances[i] : "");
        }
        tag.putIntArray("arrows", arrows); tag.putString("route", route); tag.putInt("theme", theme);
        tag.putInt("speed", speed); tag.putBoolean("enabled", enabled);
        tag.put("design", design.save());
    }
    @Override protected void read(CompoundTag tag) {
        pos = NbtPositions.getNbtBlockPos(tag, "pos"); lines = new String[4]; distances = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = tag.getString("line" + i);
            distances[i] = tag.getString("distance" + i);
        }
        arrows = tag.getIntArray("arrows"); route = tag.getString("route"); theme = tag.getInt("theme");
        speed = tag.getInt("speed"); enabled = tag.getBoolean("enabled");
        design = RoadSignDesign.load(tag.getCompound("design"));
    }
    public static void handle(RoadInformationSignPacket packet, NetworkPacketContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && !player.isSpectator()
                && (player.getMainHandItem().is(ModItemTags.WRENCHES) || player.getOffhandItem().is(ModItemTags.WRENCHES))
                && player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) <= 64
                && player.level().isLoaded(packet.pos) && player.level().mayInteract(player, packet.pos)
                && player.level().getBlockEntity(packet.pos) instanceof RoadInformationSignBlockEntity sign) {
            sign.configure(packet.lines, packet.arrows, packet.distances, packet.route, packet.theme, packet.speed, packet.enabled, packet.design);
        }
    }
}
