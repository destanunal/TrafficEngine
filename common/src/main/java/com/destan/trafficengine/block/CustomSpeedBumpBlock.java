package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CustomSpeedBumpBlock extends HorizontalDirectionalBlock {
    private final VoxelShape shapeNS; // Kuzey-Güney yönü çarpışma kutusu
    private final VoxelShape shapeEW; // Doğu-Batı yönü çarpışma kutusu

    public CustomSpeedBumpBlock(Properties properties, VoxelShape shapeNS, VoxelShape shapeEW) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        this.shapeNS = shapeNS;
        this.shapeEW = shapeEW;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Bloğun baktığı yöne göre doğru fiziksel kutuyu döndürür
        Direction dir = state.getValue(FACING);
        return (dir == Direction.EAST || dir == Direction.WEST) ? shapeEW : shapeNS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Koyulduğu zaman oyuncuya dönük olmasını sağlar
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}