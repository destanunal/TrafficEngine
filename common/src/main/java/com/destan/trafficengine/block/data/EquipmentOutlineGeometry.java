package com.destan.trafficengine.block.data;

import com.destan.trafficengine.block.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Finite block-state cache. Only visible housing/carrier portions are outlined. */
public final class EquipmentOutlineGeometry {
    private static final Map<BlockState,float[]> CACHE=new IdentityHashMap<>();
    private EquipmentOutlineGeometry() {}
    public static boolean supports(BlockState state) {
        return state.getBlock() instanceof TrafficLightBlock || state.getBlock() instanceof RoadInformationSignBlock
            || state.getBlock() instanceof ChevronPanelBlock || state.getBlock() instanceof SlopedPedestrianRailingBlock;
    }
    public static float[] edges(BlockState state) {
        if(state.hasProperty(TrafficLightBlock.WATERLOGGED))state=state.setValue(TrafficLightBlock.WATERLOGGED,false);
        if(state.getBlock() instanceof ChevronPanelBlock) {
            // These settings only change the printed face/caps, never the envelope.
            state=state.setValue(ChevronPanelBlock.ARROW_RIGHT,false).setValue(ChevronPanelBlock.RED_WHITE,false)
                .setValue(ChevronPanelBlock.LEFT,false).setValue(ChevronPanelBlock.RIGHT,false);
        }
        if(state.getBlock() instanceof SlopedPedestrianRailingBlock) {
            state=state.setValue(SlopedPedestrianRailingBlock.GRAY,true);
            int rise=state.getValue(SlopedPedestrianRailingBlock.RISE);
            if(state.getValue(SlopedPedestrianRailingBlock.DESCENDING))rise=Math.min(rise,8-state.getValue(SlopedPedestrianRailingBlock.SINK));
            state=state.setValue(SlopedPedestrianRailingBlock.RISE,rise);
            if(rise==0)state=state.setValue(SlopedPedestrianRailingBlock.DESCENDING,false);
        }
        float[] edges=CACHE.get(state);
        if(edges!=null)return edges;
        List<float[]> parts=new ArrayList<>();
        if(state.getBlock() instanceof TrafficLightBlock)light(state,parts);
        else if(state.getBlock() instanceof RoadInformationSignBlock)road(state,parts);
        else if(state.getBlock() instanceof ChevronPanelBlock)chevron(state,parts);
        else if(state.getBlock() instanceof SlopedPedestrianRailingBlock)railing(state,parts);
        int size=0;for(float[] p:parts)size+=p.length;
        edges=new float[size];int offset=0;for(float[] p:parts){System.arraycopy(p,0,edges,offset,p.length);offset+=p.length;}
        if(CACHE.size()>=512)CACHE.clear();CACHE.put(state,edges);return edges;
    }
    private static void box(List<float[]> parts,Direction facing,boolean diagonal,double x,double y,double z,double X,double Y,double Z) {
        if(X>x&&Y>y&&Z>z)parts.add(TrafficSignOutlineGeometry.box(new AABB(x/16,y/16,z/16,X/16,Y/16,Z/16),facing,diagonal));
    }
    private static void railing(BlockState state,List<float[]> parts) {
        Direction facing=state.getValue(SlopedPedestrianRailingBlock.FACING);
        double drop=state.getValue(SlopedPedestrianRailingBlock.SINK)*2;
        double rise=state.getValue(SlopedPedestrianRailingBlock.RISE)*(state.getValue(SlopedPedestrianRailingBlock.DESCENDING)?-2:2);
        double margin=Math.abs(rise)/16;
        parts.add(TrafficSignOutlineGeometry.shearedBox(new AABB(7D/16,(-drop-margin)/16,-8.9/16,9D/16,(16-drop+margin)/16,9D/16),facing,rise/16));
        if(state.getValue(SlopedPedestrianRailingBlock.BACK))box(parts,facing,false,7.35,2.5-drop,9,8.65,16-drop,16);
    }
    private static void chevron(BlockState state,List<float[]> parts) {
        Direction facing=state.getValue(ChevronPanelBlock.FACING);
        boolean diagonal=state.getValue(ChevronPanelBlock.DIAGONAL),single=state.getValue(ChevronPanelBlock.SINGLE);
        // Include the thin reflective face/end caps. Do not outline the hidden rear stem.
        box(parts,facing,diagonal,single?4:0,6,6.875,single?12:16,14,8);
        box(parts,facing,diagonal,7,0,7,9,6,9);
    }
    private static void light(BlockState state,List<float[]> parts) {
        Direction facing=state.getValue(TrafficLightBlock.FACING);
        TrafficLightModel model=state.getValue(TrafficLightBlock.MODEL);
        if(state.getBlock() instanceof HorizontalTrafficLightBlock) {
            double half=2.5*(model.getLightsCount()-1),left=4.5-half,right=11.5+half;
            box(parts,facing,false,left,4.5,.5,right,11.5,7);
            if(state.getValue(HorizontalTrafficLightBlock.VERTICAL_MOUNT)) {
                if(state.getValue(HorizontalTrafficLightBlock.BOTTOM_SUPPORT))box(parts,facing,false,7,0,7,9,4.5,9);
                else box(parts,facing,false,7,11.5,7,9,16,9);
            } else {
                box(parts,facing,false,0,7,7,Math.max(0,left),9,9);
                box(parts,facing,false,Math.min(16,right),7,7,16,9,9);
            }
            return;
        }
        boolean diagonal=state.getValue(TrafficLightBlock.DIAGONAL);
        double bottom=model.getHitboxBottom();
        body(parts,model,facing,diagonal);
        if(state.getBlock() instanceof DoubleTrafficLightBlock) {
            var back=state.getValue(DoubleTrafficLightBlock.BACK_MODEL);
            body(parts,back,facing.getOpposite(),diagonal);bottom=Math.min(bottom,back.getHitboxBottom());
        }
        box(parts,facing,false,7,0,7,9,Math.max(0,bottom),9);
    }
    private static void body(List<float[]> parts,TrafficLightModel model,Direction facing,boolean diagonal) {
        box(parts,facing,diagonal,4,model.getHitboxBottom(),model==TrafficLightModel.FOUR_LIGHTS?.5:1,12,model.getHitboxTop(),6);
    }
    private static void road(BlockState state,List<float[]> parts) {
        var kind=((RoadInformationSignBlock)state.getBlock()).kind();
        Direction facing=state.getValue(RoadInformationSignBlock.FACING);
        boolean mounted=state.getValue(RoadInformationSignBlock.POST_MOUNTED),wall=state.getValue(RoadInformationSignBlock.WALL_MOUNTED),
            hanging=state.getValue(RoadInformationSignBlock.HANGING)&&!mounted,diagonal=state.getValue(RoadInformationSignBlock.DIAGONAL);
        double baseTop=RoadInformationSignBlock.topHeight(kind,mounted),shift=hanging?16-baseTop:0;
        double bottom=(kind==RoadInformationSignBlock.Kind.LARGE&&mounted?-16:kind==RoadInformationSignBlock.Kind.DIRECTION?2:kind==RoadInformationSignBlock.Kind.HIGHWAY&&mounted?-4:0)+shift;
        double left=kind==RoadInformationSignBlock.Kind.LARGE?-24:kind==RoadInformationSignBlock.Kind.VARIABLE_SPEED?0:-8;
        double right=kind==RoadInformationSignBlock.Kind.LARGE?40:kind==RoadInformationSignBlock.Kind.VARIABLE_SPEED?16:24;
        double front=RoadInformationSignBlock.frontDepth(mounted,wall);
        box(parts,facing,diagonal,left,bottom,front,right,baseTop+shift,front+RoadInformationSignBlock.plateThickness(kind));
        if(!mounted&&!wall)box(parts,facing,diagonal,7,shift,7,9,Math.max(shift,bottom),9);
    }
}
