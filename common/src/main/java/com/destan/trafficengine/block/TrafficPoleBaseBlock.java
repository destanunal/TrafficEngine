package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A separate footing; the neck appears only while a vertical pole is above it. */
public class TrafficPoleBaseBlock extends Block implements SimpleWaterloggedBlock, ITrafficPostLike {
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape BASE = Block.box(4, 0, 4, 12, 6, 12);
    private static final VoxelShape MOUNTED = Shapes.or(BASE, Block.box(5.75, 6, 5.75, 10.25, 7.5, 10.25), Block.box(6.5, 7.5, 6.5, 9.5, 9, 9.5), Block.box(7, 9, 7, 9, 16, 9));
    public TrafficPoleBaseBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(WATERLOGGED, false));
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            BlockState pole = ModBlocks.TRAFFIC_SIGN_POST.get().defaultBlockState()
                .setValue(TrafficSignPostBlock.WATERLOGGED, state.getValue(WATERLOGGED))
                .setValue(TrafficSignPostBlock.BASE_SUPPORT, true);
            for (Direction side : Direction.values()) {
                BlockPos other = pos.relative(side);
                pole = pole.updateShape(side, level.getBlockState(other), level, pos, other);
            }
            level.setBlockAndUpdate(pos, pole);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    static boolean hasPole(BlockState state) {
        if (state.getBlock() instanceof TrafficSignPostBlock) return state.getValue(TrafficSignPostBlock.AXIS) == Direction.Axis.Y;
        return state.getBlock() instanceof ITrafficPostLike post && post.canConnect(state, Direction.DOWN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(CONNECTED, hasPole(context.getLevel().getBlockState(context.getClickedPos().above())))
            .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return side == Direction.UP ? state.setValue(CONNECTED, hasPole(neighbour)) : state;
    }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override public boolean canConnect(BlockState state, Direction side) { return side == Direction.UP; }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction side) { return side == Direction.UP; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(CONNECTED) ? MOUNTED : BASE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CONNECTED,WATERLOGGED); }
}
