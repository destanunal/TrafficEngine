package com.destan.trafficengine.block.data;

import com.destan.trafficengine.block.RoadInformationSignBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Sample in front of the physical panel, rather than inside its rear support. */
public final class RoadSignLightPosition {
    private RoadSignLightPosition() {}
    public static BlockPos sample(BlockPos origin, BlockState state) {
        var sign=(RoadInformationSignBlock)state.getBlock();
        boolean mounted=state.getValue(RoadInformationSignBlock.POST_MOUNTED);
        boolean wall=state.getValue(RoadInformationSignBlock.WALL_MOUNTED);
        // Stay half a block ahead of the panel, including post and flush-wall mounts.
        double z=RoadInformationSignBlock.frontDepth(sign.kind(),mounted,wall)/16-.5;
        double x=.5;
        if(state.getValue(RoadInformationSignBlock.DIAGONAL)) {
            double dz=z-.5;
            x=.5-dz*Math.sqrt(.5);z=.5+dz*Math.sqrt(.5);
        }
        Direction facing=state.getValue(RoadInformationSignBlock.FACING);
        double wx=switch(facing){case EAST->1-z;case SOUTH->1-x;case WEST->z;default->x;};
        double wz=switch(facing){case EAST->x;case SOUTH->1-z;case WEST->1-x;default->z;};
        // Match the original origin height; rear supports must not alter a whole panel's colour.
        return origin.offset((int)Math.floor(wx),0,(int)Math.floor(wz));
    }
}
