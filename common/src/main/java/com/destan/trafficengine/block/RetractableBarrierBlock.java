package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A portable, expandable scissor barrier. */
public class RetractableBarrierBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");

    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
        Block.box(0.5D, 0.0D, 5.5D, 2.5D, 16.0D, 10.5D),
        Block.box(13.5D, 0.0D, 5.5D, 15.5D, 16.0D, 10.5D),
        Block.box(2.0D, 3.5D, 6.5D, 14.0D, 14.0D, 9.5D)
    );
    private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
        Block.box(5.5D, 0.0D, 0.5D, 10.5D, 16.0D, 2.5D),
        Block.box(5.5D, 0.0D, 13.5D, 10.5D, 16.0D, 15.5D),
        Block.box(6.5D, 3.5D, 2.0D, 9.5D, 14.0D, 14.0D)
    );

    public RetractableBarrierBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion());
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LEFT_CONNECTED, false)
            .setValue(RIGHT_CONNECTED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH_SHAPE : EAST_WEST_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(LEFT_CONNECTED, connectsTo(context.getLevel().getBlockState(pos.relative(facing.getCounterClockWise())), facing))
            .setValue(RIGHT_CONNECTED, connectsTo(context.getLevel().getBlockState(pos.relative(facing.getClockWise())), facing));
    }

    private boolean connectsTo(BlockState neighbor, Direction facing) {
        return neighbor.is(this) && neighbor.getValue(FACING).getAxis() == facing.getAxis();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getCounterClockWise()) {
            return state.setValue(LEFT_CONNECTED, connectsTo(neighborState, facing));
        }
        if (direction == facing.getClockWise()) {
            return state.setValue(RIGHT_CONNECTED, connectsTo(neighborState, facing));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT_CONNECTED, RIGHT_CONNECTED);
    }
}
