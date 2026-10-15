package com.destan.trafficengine.network.packets.cts;
import com.destan.trafficengine.util.ItemData;

import com.destan.trafficengine.Constants;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.item.RoadPainting;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public class PaintBrushPacket extends NetworkPacketData {
    private int pattern, lineStyle, width, dashGap, linePosition, areaStyle, areaShape, pathShape;
    private boolean batch, offhand;
    public PaintBrushPacket(PacketStatus status) { super(status); }
    public PaintBrushPacket(int pattern, int lineStyle, boolean batch, int width, int dashGap, int linePosition, int areaStyle, int areaShape, InteractionHand hand) {
        this(pattern,lineStyle,batch,width,dashGap,linePosition,areaStyle,areaShape,0,hand);
    }
    public PaintBrushPacket(int pattern, int lineStyle, boolean batch, int width, int dashGap, int linePosition, int areaStyle, int areaShape, int pathShape, InteractionHand hand) {
        super(PacketStatus.OK); this.pattern=pattern; this.lineStyle=lineStyle; this.batch=batch;
        this.width=com.destan.trafficengine.item.RoadPaintPlan.normalizeWidth(width); this.offhand=hand==InteractionHand.OFF_HAND;
        this.dashGap=dashGap; this.linePosition=Math.max(0,Math.min(2,linePosition));
        this.pathShape=com.destan.trafficengine.item.RoadCurveGeometry.normalize(pathShape);
        this.linePosition=com.destan.trafficengine.item.RoadPaintPlan.normalizePosition(batch,this.width,this.pathShape,this.linePosition);
        this.areaStyle=Math.max(0,Math.min(3,areaStyle));this.areaShape=Math.max(0,Math.min(2,areaShape));
    }
    @Override protected void write(CompoundTag nbt) {
        nbt.putInt("Data",pattern);nbt.putInt("line_style",lineStyle);nbt.putBoolean("batch",batch);
        nbt.putInt("width",width);nbt.putBoolean("offhand",offhand);
        nbt.putInt("dash_gap",dashGap);nbt.putInt("line_position",linePosition);
        nbt.putInt("path_shape",pathShape);
        nbt.putInt("area_style",areaStyle);nbt.putInt("area_shape",areaShape);
    }
    @Override protected void read(CompoundTag nbt) {
        pattern=nbt.getInt("Data");lineStyle=nbt.contains("line_style")?nbt.getInt("line_style"):-1;
        batch=nbt.getBoolean("batch");width=nbt.getInt("width");offhand=nbt.getBoolean("offhand");
        dashGap=nbt.getInt("dash_gap")==4?4:2;linePosition=Math.max(0,Math.min(2,nbt.getInt("line_position")));
        pathShape=com.destan.trafficengine.item.RoadCurveGeometry.normalize(nbt.getInt("path_shape"));
        linePosition=com.destan.trafficengine.item.RoadPaintPlan.normalizePosition(batch,width,pathShape,linePosition);
        areaStyle=Math.max(0,Math.min(3,nbt.getInt("area_style")));areaShape=Math.max(0,Math.min(2,nbt.getInt("area_shape")));
    }
    public static void handle(PaintBrushPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer sender) || packet.pattern<0 || packet.pattern>=Constants.MAX_ASPHALT_PATTERNS) return;
        var stack=sender.getItemInHand(packet.offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof BrushItem)) return;
        CompoundTag tag=ItemData.getOrCreate(stack);tag.putInt(BrushItem.NBT_PATTERN,packet.pattern);
        tag.putInt(RoadPainting.STYLE,Math.max(-1,Math.min(3,packet.lineStyle)));
        tag.putBoolean(RoadPainting.BATCH,packet.batch);tag.putInt(RoadPainting.WIDTH,com.destan.trafficengine.item.RoadPaintPlan.normalizeWidth(packet.width));
        tag.putInt(RoadPainting.DASH_GAP,packet.dashGap==4?4:2);
        tag.putInt(RoadPainting.POSITION,com.destan.trafficengine.item.RoadPaintPlan.normalizePosition(packet.batch,packet.width,packet.pathShape,packet.linePosition));
        tag.putInt(RoadPainting.PATH,packet.pathShape);
        tag.putInt(RoadPainting.AREA_STYLE,packet.areaStyle);tag.putInt(RoadPainting.AREA_SHAPE,packet.areaShape);
        RoadPainting.clearSelection(tag);ItemData.set(stack,tag);sender.getInventory().setChanged();
    }
}
