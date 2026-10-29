package com.destan.trafficengine.block;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;

/** Small immutable geometry tables; only placement/neighbor events inspect terrain. */
public final class RailingGeometry {
    private RailingGeometry(){}
    public static boolean isLayeredSupport(BlockState state){
        return state.getBlock() instanceof AsphaltSlope || state.getBlock() instanceof PaintedAsphaltSlope
            || state.getBlock() instanceof AsphaltCurbSlope || state.getBlock() instanceof SidewalkSlopeBlock;
    }
    public static boolean layerHeightChanged(BlockState before,BlockState after){
        boolean a=isLayeredSupport(before),b=isLayeredSupport(after);
        return (a||b)&&(a!=b||before.getValue(BlockStateProperties.LAYERS)!=after.getValue(BlockStateProperties.LAYERS));
    }
    public static int sinkFor(BlockState below){
        if(below.getBlock() instanceof PedestrianRailingBlock)return below.getValue(PedestrianRailingBlock.SINK);
        return isLayeredSupport(below)
            &&below.hasProperty(BlockStateProperties.LAYERS)?8-below.getValue(BlockStateProperties.LAYERS):0;
    }
    public static VoxelShape rotate(VoxelShape north,Direction facing){
        int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        VoxelShape result=north;
        for(int i=0;i<turns;i++){
            final VoxelShape[] next={Shapes.empty()};
            result.forAllBoxes((x0,y0,z0,x1,y1,z1)->next[0]=Shapes.or(next[0],Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));
            result=next[0].optimize();
        }
        return result;
    }
}
