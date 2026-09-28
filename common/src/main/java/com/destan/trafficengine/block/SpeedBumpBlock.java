package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public class SpeedBumpBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Dar ve yuksek tumsegin egimli modeline oturan kademeli hitbox.
    private static final VoxelShape SHAPE_NS = Shapes.or(
            Block.box(0.0D, 0.0D, 4.0D, 16.0D, 0.75D, 12.0D),
            Block.box(0.0D, 0.75D, 5.0D, 16.0D, 1.5D, 11.0D),
            Block.box(0.0D, 1.5D, 6.0D, 16.0D, 2.25D, 10.0D),
            Block.box(0.0D, 2.25D, 7.0D, 16.0D, 2.75D, 9.0D)
    );

    private static final VoxelShape SHAPE_EW = Shapes.or(
            Block.box(4.0D, 0.0D, 0.0D, 12.0D, 0.75D, 16.0D),
            Block.box(5.0D, 0.75D, 0.0D, 11.0D, 1.5D, 16.0D),
            Block.box(6.0D, 1.5D, 0.0D, 10.0D, 2.25D, 16.0D),
            Block.box(7.0D, 2.25D, 0.0D, 9.0D, 2.75D, 16.0D)
    );

    public SpeedBumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction dir = pState.getValue(FACING);
        return (dir == Direction.EAST || dir == Direction.WEST) ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }
}