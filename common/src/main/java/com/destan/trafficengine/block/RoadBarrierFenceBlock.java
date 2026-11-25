package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RoadBarrierFenceBlock extends TrafficConeBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Each block supplies the inside half of the foot and post at either end.
    // Aligned panels meet at the cell boundary without changing saved states.
    private static final VoxelShape FEET = Shapes.or(
        Block.box(0, 0, 3.3, 2.55, 1.24, 12.7),
        Block.box(13.45, 0, 3.3, 16, 1.24, 12.7)
    );
    private static final VoxelShape[] OUTLINES = new VoxelShape[4];
    private static final VoxelShape[] COLLISIONS = new VoxelShape[4];

    static {
        VoxelShape outline = Shapes.or(FEET, posts(15.75),
            Block.box(1.18, 5, 6.82, 14.82, 8.68, 9.18),
            Block.box(1.18, 11.62, 6.82, 14.82, 15.3, 9.18)).optimize();
        VoxelShape collision = Shapes.or(FEET, posts(24),
            Block.box(0, 5, 6.82, 16, 24, 9.18)).optimize();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            OUTLINES[facing.get2DDataValue()] = RailingGeometry.rotate(outline, facing);
            COLLISIONS[facing.get2DDataValue()] = RailingGeometry.rotate(collision, facing);
        }
    }

    private static VoxelShape posts(double top) {
        return Shapes.or(
            Block.box(0, 1.18, 6.58, 1.42, top, 9.42),
            Block.box(14.58, 1.18, 6.58, 16, top, 9.42)
        );
    }

    public RoadBarrierFenceBlock() {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState neighbor, Direction side) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        if (side.getAxis().isHorizontal() && side.getAxis() != axis
                && neighbor.is(this) && neighbor.getValue(FACING).getAxis() == axis) {
            return true;
        }
        return super.skipRendering(state, neighbor, side);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
