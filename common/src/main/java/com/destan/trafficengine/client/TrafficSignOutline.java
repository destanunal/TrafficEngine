package com.destan.trafficengine.client;

import com.destan.trafficengine.block.TrafficSignBlock;
import com.destan.trafficengine.block.DoubleSidedTrafficSignBlock;
import com.destan.trafficengine.block.data.TrafficSignOutlineGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Draw directly into vanilla's existing selection buffer, with no extra pass or world reads. */
public final class TrafficSignOutline {
    private TrafficSignOutline() {}
    public static boolean render(PoseStack pose,VertexConsumer lines,BlockState state,BlockPos pos,double cameraX,double cameraY,double cameraZ) {
        if(com.destan.trafficengine.block.data.EquipmentOutlineGeometry.supports(state)) {
            draw(pose.last(),lines,com.destan.trafficengine.block.data.EquipmentOutlineGeometry.edges(state),pos.getX()-cameraX,pos.getY()-cameraY,pos.getZ()-cameraZ);
            return true;
        }
        if(GroundFeatureOutline.supports(state))return GroundFeatureOutline.render(pose,lines,state,pos,cameraX,cameraY,cameraZ);
        if(!(state.getBlock() instanceof TrafficSignBlock) || state.getValue(TrafficSignBlock.DIRECT_MOUNTED)>0
                || state.getValue(TrafficSignBlock.WALL_MOUNTED))return false;
        boolean tilted=state.getValue(TrafficSignBlock.POST_MOUNTED),diagonal=state.getValue(TrafficSignBlock.DIAGONAL);
        if(tilted&&state.getBlock() instanceof DoubleSidedTrafficSignBlock)return false;
        var facing=state.getValue(TrafficSignBlock.FACING);var shape=state.getValue(TrafficSignBlock.SHAPE);
        double x=pos.getX()-cameraX,y=pos.getY()-cameraY,z=pos.getZ()-cameraZ;
        draw(pose.last(),lines,TrafficSignOutlineGeometry.plate(shape,facing,tilted,diagonal),x,y,z);
        if(!tilted) {
            var poleShape=shape;
            if(state.getBlock() instanceof DoubleSidedTrafficSignBlock) {
                var back=state.getValue(DoubleSidedTrafficSignBlock.BACK_SHAPE);
                if(TrafficSignOutlineGeometry.plateBottom(back)<TrafficSignOutlineGeometry.plateBottom(shape))poleShape=back;
            }
            draw(pose.last(),lines,TrafficSignOutlineGeometry.pole(poleShape,facing),x,y,z);
            if(state.getBlock() instanceof DoubleSidedTrafficSignBlock)
                draw(pose.last(),lines,TrafficSignOutlineGeometry.plate(state.getValue(DoubleSidedTrafficSignBlock.BACK_SHAPE),facing.getOpposite(),false,diagonal),x,y,z);
        }
        return true;
    }
    private static void draw(PoseStack.Pose pose,VertexConsumer consumer,float[] edges,double x,double y,double z) {
        for(int i=0;i<edges.length;i+=9)for(int endpoint=0;endpoint<2;endpoint++) {
            int p=i+endpoint*3;
            consumer.addVertex(pose.pose(),(float)(x+edges[p]),(float)(y+edges[p+1]),(float)(z+edges[p+2]))
                .setColor(0F,0F,0F,.4F).setNormal(pose,edges[i+6],edges[i+7],edges[i+8]);
        }
    }
}
