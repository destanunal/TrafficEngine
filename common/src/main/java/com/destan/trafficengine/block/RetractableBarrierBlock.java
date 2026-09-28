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

    private static final VoxelShape NORTH_SOUTH_CORE_SHAPE = Shapes.or(
        Block.box(-2.0D, 2.0D, 6.5D, 0.0D, 5.0D, 9.5D),
        Block.box(-2.0D, 12.0D, 6.5D, 0.0D, 15.0D, 9.5D),
        Block.box(0.0D, 3.5D, 6.5D, 2.0D, 6.5D, 9.5D),
        Block.box(0.0D, 10.5D, 6.5D, 2.0D, 13.5D, 9.5D),
        Block.box(2.0D, 5.5D, 6.5D, 4.0D, 8.5D, 9.5D),
        Block.box(2.0D, 8.5D, 6.5D, 4.0D, 11.5D, 9.5D),
        Block.box(4.0D, 5.5D, 6.5D, 6.0D, 8.5D, 9.5D),
        Block.box(4.0D, 8.5D, 6.5D, 6.0D, 11.5D, 9.5D),
        Block.box(6.0D, 3.5D, 6.5D, 8.0D, 6.5D, 9.5D),
        Block.box(6.0D, 10.5D, 6.5D, 8.0D, 13.5D, 9.5D),
        Block.box(8.0D, 3.5D, 6.5D, 10.0D, 6.5D, 9.5D),
        Block.box(8.0D, 10.5D, 6.5D, 10.0D, 13.5D, 9.5D),
        Block.box(10.0D, 5.5D, 6.5D, 12.0D, 8.5D, 9.5D),
        Block.box(10.0D, 8.5D, 6.5D, 12.0D, 11.5D, 9.5D),
        Block.box(12.0D, 5.5D, 6.5D, 14.0D, 8.5D, 9.5D),
        Block.box(12.0D, 8.5D, 6.5D, 14.0D, 11.5D, 9.5D),
        Block.box(14.0D, 3.5D, 6.5D, 16.0D, 6.5D, 9.5D),
        Block.box(14.0D, 10.5D, 6.5D, 16.0D, 13.5D, 9.5D),
        Block.box(16.0D, 2.0D, 6.5D, 18.0D, 5.0D, 9.5D),
        Block.box(16.0D, 12.0D, 6.5D, 18.0D, 15.0D, 9.5D)
    );
    private static final VoxelShape EAST_WEST_CORE_SHAPE = Shapes.or(
        Block.box(6.5D, 2.0D, -2.0D, 9.5D, 5.0D, 0.0D),
        Block.box(6.5D, 12.0D, -2.0D, 9.5D, 15.0D, 0.0D),
        Block.box(6.5D, 3.5D, 0.0D, 9.5D, 6.5D, 2.0D),
        Block.box(6.5D, 10.5D, 0.0D, 9.5D, 13.5D, 2.0D),
        Block.box(6.5D, 5.5D, 2.0D, 9.5D, 8.5D, 4.0D),
        Block.box(6.5D, 8.5D, 2.0D, 9.5D, 11.5D, 4.0D),
        Block.box(6.5D, 5.5D, 4.0D, 9.5D, 8.5D, 6.0D),
        Block.box(6.5D, 8.5D, 4.0D, 9.5D, 11.5D, 6.0D),
        Block.box(6.5D, 3.5D, 6.0D, 9.5D, 6.5D, 8.0D),
        Block.box(6.5D, 10.5D, 6.0D, 9.5D, 13.5D, 8.0D),
        Block.box(6.5D, 3.5D, 8.0D, 9.5D, 6.5D, 10.0D),
        Block.box(6.5D, 10.5D, 8.0D, 9.5D, 13.5D, 10.0D),
        Block.box(6.5D, 5.5D, 10.0D, 9.5D, 8.5D, 12.0D),
        Block.box(6.5D, 8.5D, 10.0D, 9.5D, 11.5D, 12.0D),
        Block.box(6.5D, 5.5D, 12.0D, 9.5D, 8.5D, 14.0D),
        Block.box(6.5D, 8.5D, 12.0D, 9.5D, 11.5D, 14.0D),
        Block.box(6.5D, 3.5D, 14.0D, 9.5D, 6.5D, 16.0D),
        Block.box(6.5D, 10.5D, 14.0D, 9.5D, 13.5D, 16.0D),
        Block.box(6.5D, 2.0D, 16.0D, 9.5D, 5.0D, 18.0D),
        Block.box(6.5D, 12.0D, 16.0D, 9.5D, 15.0D, 18.0D)
    );
    private static final VoxelShape WEST_SUPPORT_SHAPE = Shapes.or(
        Block.box(0.75D, 2.25D, 6.5D, 2.25D, 15.5D, 9.5D),
        Block.box(0.0D, 0.0D, 5.5D, 3.0D, 2.5D, 10.5D)
    );
    private static final VoxelShape EAST_SUPPORT_SHAPE = Shapes.or(
        Block.box(13.75D, 2.25D, 6.5D, 15.25D, 15.5D, 9.5D),
        Block.box(13.0D, 0.0D, 5.5D, 16.0D, 2.5D, 10.5D)
    );
    private static final VoxelShape NORTH_SUPPORT_SHAPE = Shapes.or(
        Block.box(6.5D, 2.25D, 0.75D, 9.5D, 15.5D, 2.25D),
        Block.box(5.5D, 0.0D, 0.0D, 10.5D, 2.5D, 3.0D)
    );
    private static final VoxelShape SOUTH_SUPPORT_SHAPE = Shapes.or(
        Block.box(6.5D, 2.25D, 13.75D, 9.5D, 15.5D, 15.25D),
        Block.box(5.5D, 0.0D, 13.0D, 10.5D, 2.5D, 16.0D)
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
        Direction facing = state.getValue(FACING);
        VoxelShape shape = facing.getAxis() == Direction.Axis.Z
            ? NORTH_SOUTH_CORE_SHAPE
            : EAST_WEST_CORE_SHAPE;
        boolean positiveFacing = facing == Direction.NORTH || facing == Direction.EAST;
        Direction conditionalSide = positiveFacing ? facing.getCounterClockWise() : facing.getClockWise();
        if (!state.getValue(positiveFacing ? LEFT_CONNECTED : RIGHT_CONNECTED)) {
            shape = Shapes.or(shape, supportShape(conditionalSide));
        }
        return Shapes.or(shape, supportShape(conditionalSide.getOpposite()));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static VoxelShape supportShape(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_SUPPORT_SHAPE;
            case SOUTH -> SOUTH_SUPPORT_SHAPE;
            case WEST -> WEST_SUPPORT_SHAPE;
            default -> EAST_SUPPORT_SHAPE;
        };
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
