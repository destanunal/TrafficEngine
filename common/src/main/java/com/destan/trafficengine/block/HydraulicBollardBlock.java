package com.destan.trafficengine.block;

import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** No entity/ticker: only eight scheduled updates while a bollard is moving. */
public final class HydraulicBollardBlock extends Block {
    public static final BooleanProperty RAISED = BooleanProperty.create("raised");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final IntegerProperty EXTENSION = IntegerProperty.create("extension", 0, 8);
    private static final VoxelShape BASE = box(3, 0, 3, 13, 0.5, 13);
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    static {
        for (int i = 0; i <= 8; i++) SHAPES[i] = i == 0 ? Shapes.empty()
            : box(5, 0, 5, 11, 0.5 + i * 1.75, 11);
    }
    public HydraulicBollardBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(3F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(RAISED, true).setValue(POWERED, false).setValue(EXTENSION, 8));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RAISED, POWERED, EXTENSION);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(POWERED, powered).setValue(RAISED, !powered).setValue(EXTENSION, powered ? 0 : 8);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered).setValue(RAISED, !powered), 3);
        level.scheduleTick(pos, this, 2);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int current = state.getValue(EXTENSION), target = state.getValue(RAISED) ? 8 : 0;
        if (current == target) return;
        int next = current + Integer.signum(target - current);
        level.setBlock(pos, state.setValue(EXTENSION, next), 3);
        if (next != target) level.scheduleTick(pos, this, 2);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide && state.getValue(EXTENSION) != (state.getValue(RAISED) ? 8 : 0))
            level.scheduleTick(pos, this, 2);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(EXTENSION) == 0 ? BASE : SHAPES[state.getValue(EXTENSION)];
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(EXTENSION)];
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(RAISED), 3);
            level.scheduleTick(pos, this, 2);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, .25F, .8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state, level, pos, player, hand, hit));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }
    @Override protected com.mojang.serialization.MapCodec<? extends Block> codec() { return com.mojang.serialization.MapCodec.unit(this); }
}
