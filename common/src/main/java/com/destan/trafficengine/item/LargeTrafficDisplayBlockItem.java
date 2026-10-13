package com.destan.trafficengine.item;

import com.destan.trafficengine.block.TrafficSignPostBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Places the display's center block above the floor so its lower row rests on it. */
public class LargeTrafficDisplayBlockItem extends BlockItem {
    public LargeTrafficDisplayBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.getClickedFace() == Direction.UP
            && !(context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof TrafficSignPostBlock)) {
            return BlockPlaceContext.at(context, context.getClickedPos().above(), Direction.UP);
        }
        return context;
    }
}
