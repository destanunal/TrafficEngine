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
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    public static final BooleanProperty ARROW_RIGHT = BooleanProperty.create("arrow_right");
    public static final BooleanProperty RED_WHITE = BooleanProperty.create("red_white");
    public static final BooleanProperty SINGLE = BooleanProperty.create("single");
    public static final BooleanProperty LEFT = BooleanProperty.create("left_connection");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right_connection");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[][] DIAGONAL_SHAPES = new VoxelShape[2][4];
    private static final VoxelShape[][] SHAPES = new VoxelShape[2][4];
    static {
        for (int size = 0; size < 2; size++) for (Direction facing : Direction.Plane.HORIZONTAL) {
            double min = size == 1 ? 4 : 0, max = 16 - min;
            VoxelShape plate = switch (facing) {
                case SOUTH -> box(min, 6, 8, max, 14, 9.125);
                case EAST -> box(8, 6, min, 9.125, 14, max);
                case WEST -> box(6.875, 6, min, 8, 14, max);
                default -> box(min, 6, 6.875, max, 14, 8);
            };
            // The rear support reaches the centre of the board, matching the baked model.
            SHAPES[size][facing.get2DDataValue()] = Shapes.or(plate, box(7, 0, 7, 9, 10, 9)).optimize();
            DIAGONAL_SHAPES[size][facing.get2DDataValue()] = com.destan.trafficengine.block.data.DiagonalVoxelShapes.rotateClockwise45(SHAPES[size][facing.get2DDataValue()]);
        }
    }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    public ChevronPanelBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion()
            // Constant vanilla block light needs no clock polling or periodic chunk updates.
            .lightLevel(state -> 7));
        registerDefaultState(defaultBlockState().setValue(DIAGONAL, false).setValue(FACING, Direction.NORTH).setValue(ARROW_RIGHT, true)
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
        return defaultBlockState().setValue(DIAGONAL, diagonal).setValue(FACING, facing)
            .setValue(LEFT, !diagonal && connects(context.getLevel().getBlockState(pos.relative(facing.getClockWise())), facing))
            .setValue(RIGHT, !diagonal && connects(context.getLevel().getBlockState(pos.relative(facing.getCounterClockWise())), facing))
            .setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (state.getValue(DIAGONAL)) return state.setValue(LEFT, false).setValue(RIGHT, false);
        Direction facing = state.getValue(FACING);
        if (side == facing.getClockWise()) return state.setValue(LEFT, !state.getValue(SINGLE) && connects(neighbor, facing));
        if (side == facing.getCounterClockWise()) return state.setValue(RIGHT, !state.getValue(SINGLE) && connects(neighbor, facing));
        return state;
    }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(DIAGONAL) ? DIAGONAL_SHAPES : SHAPES)[state.getValue(SINGLE) ? 1 : 0][state.getValue(FACING).get2DDataValue()];
    }
    @Override public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIAGONAL, FACING, ARROW_RIGHT, RED_WHITE, SINGLE, LEFT, RIGHT, WATERLOGGED);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(ARROW_RIGHT, !state.getValue(ARROW_RIGHT)).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT))
            .setValue(FACING, state.getValue(DIAGONAL) ? mirror.mirror(state.getValue(FACING)).getCounterClockWise() : mirror.mirror(state.getValue(FACING)));
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state, level, pos, player, hand, hit));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (level.isClientSide) ClientWrapper.showChevronPanelSettings(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction side) { return side.getAxis().isVertical(); }
    @Override public boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
}
