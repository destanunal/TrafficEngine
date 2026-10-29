package com.destan.trafficengine.client;

import com.destan.trafficengine.block.*;
import com.destan.trafficengine.client.model.SelectionMeshEdges;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Sixty-four static mesh entries, invalidated on resource reload; no world scans or extra passes. */
public final class GroundFeatureOutline {
    private record Cached(BakedModel model,float[] edges) {}
    private static final Map<BlockState,Cached> CACHE=new LinkedHashMap<>(64,.75F,true);
    private GroundFeatureOutline() {}
    public static void invalidate(){CACHE.clear();}
    public static boolean supports(BlockState state) {
        return state.getBlock() instanceof SpeedBumpBlock || state.getBlock() instanceof CustomSpeedBumpBlock
            || state.getBlock() instanceof AsphaltSpeedBumpBlock || state.getBlock() instanceof BikeLaneSeparatorBlock
            || state.getBlock() instanceof CurvedTrafficPoleBlock;
    }
    public static boolean render(PoseStack pose,VertexConsumer lines,BlockState state,BlockPos pos,double cameraX,double cameraY,double cameraZ) {
        if(!supports(state))return false;
        if(state.getBlock() instanceof CurvedTrafficPoleBlock) {
            state=state.setValue(CurvedTrafficPoleBlock.WATERLOGGED,false);
            if(state.getValue(CurvedTrafficPoleBlock.PART)!=5)state=state.setValue(CurvedTrafficPoleBlock.LAMP_SUPPORT,false);
        }
        BlockState meshState=state;float offsetX=0,offsetY=0,offsetZ=0;
        if(state.getBlock() instanceof CurvedTrafficPoleBlock) {
            int part=state.getValue(CurvedTrafficPoleBlock.PART);
            // Cells 1/2 are invisible picking helpers: their surface belongs to
            // part 0. Reuse that model in local coordinates without reading neighbours.
            if(part==1||part==2) {
                meshState=state.setValue(CurvedTrafficPoleBlock.PART,0);
                if(part==1)offsetY=-1;
                else {Direction facing=state.getValue(CurvedTrafficPoleBlock.FACING);offsetX=-facing.getStepX();offsetZ=-facing.getStepZ();}
            }
        }
        BakedModel model=Minecraft.getInstance().getBlockRenderer().getBlockModel(meshState);
        Cached cached=CACHE.get(state);
        if(cached==null || cached.model()!=model) {
            List<float[]> faces=new ArrayList<>();RandomSource random=RandomSource.create(0);
            for(int side=-1;side<6;side++) {
                random.setSeed(0);
                for(var quad:model.getQuads(meshState,side<0?null:Direction.from3DDataValue(side),random)) {
                    if(state.getBlock() instanceof AsphaltSpeedBumpBlock && quad.isTinted())continue;
                    int[] packed=quad.getVertices();int stride=packed.length/4;float[] face=new float[12];
                    for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++)face[v*3+axis]=Float.intBitsToFloat(packed[v*stride+axis]);
                    for(int vertex=0;vertex<4;vertex++){face[vertex*3]+=offsetX;face[vertex*3+1]+=offsetY;face[vertex*3+2]+=offsetZ;}
                    faces.add(face);
                }
            }
            cached=new Cached(model,SelectionMeshEdges.build(faces));
            if(CACHE.size()>=64)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(state,cached);
        }
        float[] edges=cached.edges();if(edges.length==0)return false;
        double x=pos.getX()-cameraX,y=pos.getY()-cameraY,z=pos.getZ()-cameraZ;
        for(int i=0;i<edges.length;i+=15)if(SelectionMeshEdges.visible(edges,i,-x,-y,-z))
            for(int endpoint=0;endpoint<2;endpoint++) {
                int p=i+endpoint*3;
                lines.addVertex(pose.last().pose(),(float)(x+edges[p]),(float)(y+edges[p+1]),(float)(z+edges[p+2]))
                    .setColor(0F,0F,0F,.4F).setNormal(pose.last(),edges[i+6],edges[i+7],edges[i+8]);
            }
        return true;
    }
}
