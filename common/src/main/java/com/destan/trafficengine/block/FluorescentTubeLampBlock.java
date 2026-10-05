package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.StreetLampShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FluorescentTubeLampBlock extends StreetLightBlock {

    
    public FluorescentTubeLampBlock() {
        super();
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        VoxelShape shape = StreetLampShapes.FLUORESCENT.get(pState.getValue(FACING));

        if (Boolean.TRUE.equals(pState.getValue(NORTH))) {
            shape = Shapes.or(shape, SHAPE_NORTH);
        }

        if (Boolean.TRUE.equals(pState.getValue(EAST))) {
            shape = Shapes.or(shape, SHAPE_EAST);
        }

        if (Boolean.TRUE.equals(pState.getValue(SOUTH))) {
            shape = Shapes.or(shape, SHAPE_SOUTH);
        }

        if (Boolean.TRUE.equals(pState.getValue(WEST))) {
            shape = Shapes.or(shape, SHAPE_WEST);
        }

        if (Boolean.TRUE.equals(pState.getValue(UP))) {
            shape = Shapes.or(shape, SHAPE_UP);
        }

        return shape;
    }
}
