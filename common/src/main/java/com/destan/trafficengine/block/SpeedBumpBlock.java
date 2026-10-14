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

    private static final double HEIGHT = 1.4497474683058327;
    // Three enclosing tiers follow the low ramps and include both ground-level toes.
    private static final VoxelShape SHAPE_NS = Shapes.or(
        Block.box(0, 0, 4, 16, HEIGHT * 0.25, 12),
        Block.box(0, HEIGHT * 0.25, 4.875, 16, HEIGHT * 0.625, 11.125),
        Block.box(0, HEIGHT * 0.625, 6.1875, 16, HEIGHT, 9.8125));
    private static final VoxelShape SHAPE_EW = Shapes.or(
        Block.box(4, 0, 0, 12, HEIGHT * 0.25, 16),
        Block.box(4.875, HEIGHT * 0.25, 0, 11.125, HEIGHT * 0.625, 16),
        Block.box(6.1875, HEIGHT * 0.625, 0, 9.8125, HEIGHT, 16));

    public SpeedBumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction dir = pState.getValue(FACING);
        return dir.getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

}