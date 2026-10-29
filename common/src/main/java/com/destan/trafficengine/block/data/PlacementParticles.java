package com.destan.trafficengine.block.data;

import com.destan.trafficengine.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Interned straight states keep effect shape, texture and count equal in both placements. */
public final class PlacementParticles {
    private PlacementParticles() {}
    public static BlockState straight(BlockState state) {
        if(state.getBlock() instanceof TrafficSignBlock)return state.setValue(TrafficSignBlock.DIAGONAL,false);
        if(state.getBlock() instanceof TrafficLightBlock)return state.setValue(TrafficLightBlock.DIAGONAL,false);
        if(state.getBlock() instanceof RoadInformationSignBlock)return state.setValue(RoadInformationSignBlock.DIAGONAL,false);
        if(state.getBlock() instanceof BikeLaneSeparatorBlock)return state.setValue(BikeLaneSeparatorBlock.DIAGONAL,false);
        if(state.getBlock() instanceof ChevronPanelBlock)return state.setValue(ChevronPanelBlock.DIAGONAL,false);
        return state;
    }
}
