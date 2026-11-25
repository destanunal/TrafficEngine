package com.destan.trafficengine.item;

import com.destan.trafficengine.block.CurvedTrafficPoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class TrafficSignBlockItem extends BlockItem {
    public TrafficSignBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos clicked = context.replacingClickedOnBlock() ? context.getClickedPos()
            : context.getClickedPos().relative(face.getOpposite());
        var world = context.getLevel();
        var support = world.getBlockState(clicked);
        if (!(support.getBlock() instanceof CurvedTrafficPoleBlock)
                || CurvedTrafficPoleBlock.isTrafficSignMount(support, face)) {
            return super.place(context);
        }

        // The two narrow picking cells belong to the first visible mast segment.
        int part = support.getValue(CurvedTrafficPoleBlock.PART);
        Direction mastFacing = support.getValue(CurvedTrafficPoleBlock.FACING);
        if (part == 1 || part == 2) {
            clicked = CurvedTrafficPoleBlock.masterPos(clicked, support);
            if (!world.isLoaded(clicked)) return InteractionResult.FAIL;
            var master = world.getBlockState(clicked);
            if (!master.is(support.getBlock()) || master.getValue(CurvedTrafficPoleBlock.PART) != 0
                    || master.getValue(CurvedTrafficPoleBlock.FACING) != mastFacing) return InteractionResult.FAIL;
            support = master;
        }

        if (!CurvedTrafficPoleBlock.isTrafficSignMount(support, face)) {
            var player = context.getPlayer();
            Vec3 viewer = player == null ? context.getClickLocation() : player.position();
            face = mastFacing.getClockWise();
            if ((viewer.x - clicked.getX() - .5) * face.getStepX()
                    + (viewer.z - clicked.getZ() - .5) * face.getStepZ() < 0) face = face.getOpposite();
        }

        BlockPos target = clicked.relative(face);
        var player = context.getPlayer();
        if (!world.isLoaded(target) || player != null && (!world.mayInteract(player, target)
                || !player.mayUseItemAt(target, face, context.getItemInHand()))) return InteractionResult.FAIL;
        var placement = BlockPlaceContext.at(context, target, face);
        return placement.getClickedPos().equals(target) ? super.place(placement) : InteractionResult.FAIL;
    }
}
