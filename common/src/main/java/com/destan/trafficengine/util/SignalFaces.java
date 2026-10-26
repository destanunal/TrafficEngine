package com.destan.trafficengine.util;

import com.destan.trafficengine.block.DoubleTrafficLightBlock;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.entity.DoubleTrafficLightBlockEntity;
import com.destan.trafficengine.data.WorldLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** One registered block entity, with separate persistent signal states and link identities. */
public final class SignalFaces {
    private SignalFaces(){}
    public static BlockEntity get(Level level,WorldLocation location) { return get(level,location.getLocationBlockPos(),location.signalFace); }
    public static BlockEntity get(Level level,BlockPos pos,int face) {
        BlockEntity entity=level.getBlockEntity(pos);
        return entity instanceof DoubleTrafficLightBlockEntity doubleLight?doubleLight.face(face):face==0?entity:null;
    }
    public static int hit(BlockState state,BlockPos pos,Vec3 point) {
        if(!(state.getBlock() instanceof DoubleTrafficLightBlock))return 0;
        var facing=state.getValue(TrafficLightBlock.FACING);double x=facing.getStepX(),z=facing.getStepZ();
        if(state.getValue(TrafficLightBlock.DIAGONAL)){double old=x;x=(x-z)*Math.sqrt(.5);z=(old+z)*Math.sqrt(.5);}
        return (point.x-pos.getX()-.5)*x+(point.z-pos.getZ()-.5)*z<0?1:0;
    }
}
