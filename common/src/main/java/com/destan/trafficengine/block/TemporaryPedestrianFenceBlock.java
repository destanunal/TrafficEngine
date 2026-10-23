package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** End posts, corners and junctions are baked multipart models; only changed neighbours are inspected. */
public final class TemporaryPedestrianFenceBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty NORTH = PipeBlock.NORTH, EAST = PipeBlock.EAST, SOUTH = PipeBlock.SOUTH, WEST = PipeBlock.WEST;
    public static final BooleanProperty EAST_WEST = BooleanProperty.create("east_west");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] SHAPES = new VoxelShape[16];
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    static {
        for (int mask = 0; mask < 16; mask++) {
            VoxelShape outline = Shapes.or(box(6, 0, 6, 10, 4, 10), box(7.25, 4, 7.25, 8.75, 32, 8.75));
            for (int side = 0; side < 4; side++) if ((mask & 1 << side) != 0) {
                VoxelShape base = arm(side, 0, 4, 6, 10, 6);
                outline = Shapes.or(outline, base, arm(side, 4, 32, 7.25, 8.75, 7.25));
            }
            SHAPES[mask] = outline.optimize();
        }
    }
    private static VoxelShape arm(int side, double bottom, double top, double min, double max, double end) {
        return switch (side) {
            case 1 -> box(16-end, bottom, min, 16, top, max);
            case 2 -> box(min, bottom, 16-end, max, top, 16);
            case 3 -> box(0, bottom, min, end, top, max);
            default -> box(min, bottom, 0, max, top, end);
        };
    }
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    public TemporaryPedestrianFenceBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
            .setValue(EAST_WEST, true).setValue(WATERLOGGED, false));
    }
    public static int connectionMask(BlockState state) {
        int mask = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
        return mask != 0 ? mask : state.getValue(EAST_WEST) ? 10 : 5;
    }
    public static BooleanProperty property(Direction side) {
        return switch (side) { case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST; default -> NORTH; };
    }
    public static boolean connects(BlockState state) { return state.getBlock() instanceof TemporaryPedestrianFenceBlock; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(EAST_WEST, context.getHorizontalDirection().getAxis() == Direction.Axis.Z)
            .setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
        for (Direction side : SIDES) state = state.setValue(property(side), connects(context.getLevel().getBlockState(pos.relative(side))));
        return state;
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return side.getAxis().isHorizontal() ? state.setValue(property(side), connects(neighbor)) : state;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[connectionMask(state)]; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[connectionMask(state)]; }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NORTH, EAST, SOUTH, WEST, EAST_WEST, WATERLOGGED); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (Direction side : SIDES) result = result.setValue(property(rotation.rotate(side)), state.getValue(property(side)));
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? result.cycle(EAST_WEST) : result;
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (Direction side : SIDES) result = result.setValue(property(mirror.mirror(side)), state.getValue(property(side)));
        return result;
    }
    @Override public boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
}
