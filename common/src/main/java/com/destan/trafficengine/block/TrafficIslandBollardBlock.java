package com.destan.trafficengine.block;

import java.util.List;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static baked geometry and one scheduled daylight check, with no ticking block entity. */
public class TrafficIslandBollardBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty KEEP_RIGHT = BooleanProperty.create("keep_right");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape NORTH = Shapes.or(Block.box(3.2, 0, 3.2, 12.8, 0.8, 12.8),
        Block.box(4.8, 0.8, 5.52, 11.2, 10.4, 10.48), Block.box(4, 5.6, 4.4, 12, 12.8, 5.6));
    private static final VoxelShape SOUTH = Shapes.or(Block.box(3.2, 0, 3.2, 12.8, 0.8, 12.8),
        Block.box(4.8, 0.8, 5.52, 11.2, 10.4, 10.48), Block.box(4, 5.6, 10.4, 12, 12.8, 11.6));
    private static final VoxelShape EAST = Shapes.or(Block.box(3.2, 0, 3.2, 12.8, 0.8, 12.8),
        Block.box(5.52, 0.8, 4.8, 10.48, 10.4, 11.2), Block.box(10.4, 5.6, 4, 11.6, 12.8, 12));
    private static final VoxelShape WEST = Shapes.or(Block.box(3.2, 0, 3.2, 12.8, 0.8, 12.8),
        Block.box(5.52, 0.8, 4.8, 10.48, 10.4, 11.2), Block.box(4.4, 5.6, 4, 5.6, 12.8, 12));

    public TrafficIslandBollardBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5F)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
            .lightLevel(state -> state.getValue(LIT) ? 8 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(KEEP_RIGHT, true).setValue(LIT, false).setValue(WATERLOGGED, false));
    }

    public static boolean shouldIlluminate(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= 12000L && time < 23000L;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(LIT, shouldIlluminate(context.getLevel().getDayTime()))
            .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 100);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean lit = shouldIlluminate(level.getDayTime());
        if (state.getValue(LIT) != lit) level.setBlockAndUpdate(pos, state.setValue(LIT, lit));
        level.scheduleTick(pos, this, 100);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockState next = player.isShiftKeyDown() ? rotate(state, Rotation.CLOCKWISE_90)
                : state.cycle(KEEP_RIGHT);
            level.setBlockAndUpdate(pos, next);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        // Reflect the sign direction as well as its mounting orientation.
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(KEEP_RIGHT, mirror == Mirror.NONE ? state.getValue(KEEP_RIGHT) : !state.getValue(KEEP_RIGHT));
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.trafficengine.traffic_island_bollard.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.trafficengine.traffic_island_bollard.tooltip.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KEEP_RIGHT, LIT, WATERLOGGED);
    }
}
