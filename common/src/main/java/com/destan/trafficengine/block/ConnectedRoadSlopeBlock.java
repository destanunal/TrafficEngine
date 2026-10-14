package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ConnectedLineStyle;
import com.destan.trafficengine.block.data.RoadType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Automatic markings retain the road's layer height and water state. */
public class ConnectedRoadSlopeBlock extends PaintedAsphaltSlope {
    public ConnectedRoadSlopeBlock(RoadType type) {
        super(type, type.getSlope());
        registerDefaultState(defaultBlockState().setValue(ConnectedRoadBlock.STYLE, ConnectedLineStyle.SOLID));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ConnectedRoadBlock.STYLE);
    }
}
