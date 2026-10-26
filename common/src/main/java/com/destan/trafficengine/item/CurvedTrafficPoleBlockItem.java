package com.destan.trafficengine.item;

import com.destan.trafficengine.block.CurvedTrafficPoleBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Places a complete arm atomically, consuming one item only after successful placement. */
public final class CurvedTrafficPoleBlockItem extends BlockItem {
    public CurvedTrafficPoleBlockItem(Block block,Properties properties){super(block,properties);}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state) {
        return ((CurvedTrafficPoleBlock)getBlock()).placeAssembly(context,state);
    }
}
