package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static, joinable curve boards. Settings live in block states, never in ticking entities. */
public final class ChevronPanelBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, ITrafficPostLike {
    public static final BooleanProperty UPPER_CONNECTION = BooleanProperty.create("upper_connection");
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    public static final BooleanProperty ARROW_RIGHT = BooleanProperty.create("arrow_right");
    public static final BooleanProperty RED_WHITE = BooleanProperty.create("red_white");
    public static final BooleanProperty SINGLE = BooleanProperty.create("single");
    public static final BooleanProperty LEFT = BooleanProperty.create("left_connection");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right_connection");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[][][] DIAGONAL_SHAPES = new VoxelShape[2][2][4];
    private static final VoxelShape[][][] SHAPES = new VoxelShape[2][2][4];
    static {
        for (int size = 0; size < 2; size++) for (int upper = 0; upper < 2; upper++) {
            double min = size == 1 ? 2 : 0, max = 16 - min;
            double bottom=size==1?2:4,top=16-bottom;
            VoxelShape panel = box(min, bottom, 4.875, max, top, 6);
            double low=bottom+.75,high=top-2.25,stop=high+.85;
            // The same two short arms and square clamp dimensions as traffic lights.
            VoxelShape arms=Shapes.or(box(7.4,low+.1,6,8.6,low+.75,7.3),box(7.4,high+.1,6,8.6,high+.75,7.3));
            VoxelShape head=Shapes.or(panel,arms);
            VoxelShape stem=box(7,0,7,9,upper==1?16:stop,9);
            VoxelShape clamps=Shapes.or(box(6.72,low,6.72,9.28,low+.85,9.28),box(6.72,high,6.72,9.28,high+.85,9.28));
            VoxelShape straight=Shapes.or(head,stem,clamps).optimize();
            VoxelShape diagonal=Shapes.or(com.destan.trafficengine.block.data.DiagonalVoxelShapes.rotateClockwise45(head),stem,clamps).optimize();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                SHAPES[size][upper][facing.get2DDataValue()] = RailingGeometry.rotate(straight, facing);
                DIAGONAL_SHAPES[size][upper][facing.get2DDataValue()] = RailingGeometry.rotate(diagonal, facing);
            }
        }
    }
    public ChevronPanelBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion()
            // Constant vanilla block light needs no clock polling or periodic chunk updates.
            .lightLevel(state -> 7));
        registerDefaultState(defaultBlockState().setValue(UPPER_CONNECTION, false).setValue(DIAGONAL, false).setValue(FACING, Direction.NORTH).setValue(ARROW_RIGHT, true)
            .setValue(RED_WHITE, true).setValue(SINGLE, false).setValue(LEFT, false).setValue(RIGHT, false).setValue(WATERLOGGED, false));
    }
    public static boolean connects(BlockState neighbor, Direction facing) {
        return neighbor.getBlock() instanceof ChevronPanelBlock && neighbor.getValue(FACING) == facing && !neighbor.getValue(SINGLE) && !neighbor.getValue(DIAGONAL);
    }
    /** Only a size change needs the two adjacent boards to be inspected. */
    public static BlockState withSingle(BlockState state, boolean single, BlockGetter level, BlockPos pos) {
        if (state.getValue(SINGLE) == single) return state;
        if (state.getValue(DIAGONAL)) return state.setValue(SINGLE, single).setValue(LEFT, false).setValue(RIGHT, false);
        Direction facing = state.getValue(FACING);
        return state.setValue(SINGLE, single)
            .setValue(LEFT, !single && connects(level.getBlockState(pos.relative(facing.getClockWise())), facing))
            .setValue(RIGHT, !single && connects(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        float rotation = context.getPlayer() == null ? context.getHorizontalDirection().toYRot() : context.getPlayer().getYRot();
        int sector = Math.floorMod((int)Math.floor((rotation + 22.5F) / 45F), 8);
        Direction facing = switch (sector / 2) { case 0 -> Direction.NORTH; case 1 -> Direction.EAST; case 2 -> Direction.SOUTH; default -> Direction.WEST; };
        boolean diagonal = (sector & 1) != 0;
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(UPPER_CONNECTION, connectsAbove(context.getLevel().getBlockState(pos.above()))).setValue(DIAGONAL, diagonal).setValue(FACING, facing)
            .setValue(LEFT, !diagonal && connects(context.getLevel().getBlockState(pos.relative(facing.getClockWise())), facing))
            .setValue(RIGHT, !diagonal && connects(context.getLevel().getBlockState(pos.relative(facing.getCounterClockWise())), facing))
            .setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
    }
    private static boolean connectsAbove(BlockState above) {
        return above.getBlock() instanceof ITrafficPostLike post && post.canConnect(above, Direction.DOWN);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (side == Direction.UP) state = state.setValue(UPPER_CONNECTION, connectsAbove(neighbor));
        if (state.getValue(DIAGONAL)) return state.setValue(LEFT, false).setValue(RIGHT, false);
        Direction facing = state.getValue(FACING);
        if (side == facing.getClockWise()) return state.setValue(LEFT, !state.getValue(SINGLE) && connects(neighbor, facing));
        if (side == facing.getCounterClockWise()) return state.setValue(RIGHT, !state.getValue(SINGLE) && connects(neighbor, facing));
        return state;
    }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(DIAGONAL) ? DIAGONAL_SHAPES : SHAPES)[state.getValue(SINGLE) ? 1 : 0][state.getValue(UPPER_CONNECTION) ? 1 : 0][state.getValue(FACING).get2DDataValue()];
    }
    @Override public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UPPER_CONNECTION, DIAGONAL, FACING, ARROW_RIGHT, RED_WHITE, SINGLE, LEFT, RIGHT, WATERLOGGED);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(ARROW_RIGHT, !state.getValue(ARROW_RIGHT)).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT))
            .setValue(FACING, state.getValue(DIAGONAL) ? mirror.mirror(state.getValue(FACING)).getCounterClockWise() : mirror.mirror(state.getValue(FACING)));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (level.isClientSide) ClientWrapper.showChevronPanelSettings(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction side) { return side.getAxis().isVertical(); }
    @Override public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
}
