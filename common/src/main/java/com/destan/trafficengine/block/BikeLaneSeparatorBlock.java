package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.DiagonalVoxelShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Eight-way placement for separators along straight and diagonal bike lanes. */
public class BikeLaneSeparatorBlock extends CustomSpeedBumpBlock {
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    private final VoxelShape diagonalNS;
    private final VoxelShape diagonalEW;

    public BikeLaneSeparatorBlock(Properties properties, VoxelShape shapeNS, VoxelShape shapeEW) {
        super(properties, shapeNS, shapeEW);
        registerDefaultState(defaultBlockState().setValue(DIAGONAL, false));
        diagonalNS = DiagonalVoxelShapes.rotateClockwise45(shapeNS, 0.5, 0.5, 4.0 / 16.0);
        diagonalEW = DiagonalVoxelShapes.rotateClockwise45(shapeEW, 0.5, 0.5, 4.0 / 16.0);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(DIAGONAL)) return super.getShape(state, level, pos, context);
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? diagonalNS : diagonalEW;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int sector = Math.floorMod((int) Math.floor(context.getRotation() / 45.0F + 0.5F), 8);
        return defaultBlockState()
            .setValue(FACING, Direction.fromYRot((sector / 2) * 90.0F))
            .setValue(DIAGONAL, (sector & 1) != 0);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Direction facing = mirror.mirror(state.getValue(FACING));
        if (state.getValue(DIAGONAL)) facing = facing.getCounterClockWise();
        return state.setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIAGONAL);
    }
}
