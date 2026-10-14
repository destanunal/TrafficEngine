package com.destan.trafficengine.block;

import com.destan.trafficengine.block.entity.BoomBarrierBlockEntity;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** One motor and eight lightweight arm cells; no server or client tickers. */
public class BoomBarrierBlock extends BaseEntityBlock {
    public static final int LENGTH = 4;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final double PIVOT_Y = 14 / 16.0;
    private static final VoxelShape BODY = Shapes.or(
        Block.box(2.5,0,2.5,13.5,1.5,13.5),
        Block.box(3.5,1.5,3.5,12.5,14,12.5),
        Block.box(3,14,3,13,16,13)).optimize();
    private static final VoxelShape[] CLOSED_BODY = new VoxelShape[4];
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape root = switch (facing) {
                case NORTH -> Block.box(6.5,13,0,9.5,15,8);
                case SOUTH -> Block.box(6.5,13,8,9.5,15,16);
                case WEST -> Block.box(0,13,6.5,8,15,9.5);
                default -> Block.box(8,13,6.5,16,15,9.5);
            };
            CLOSED_BODY[facing.get2DDataValue()] = Shapes.or(BODY,root).optimize();
        }
    }
    /** Includes the arm's full sweep; allocated once per entity/facing, not per rendered frame. */
    public static AABB renderBounds(BlockPos pos, Direction facing) {
        int dx=facing.getStepX()*LENGTH, dz=facing.getStepZ()*LENGTH;
        return new AABB(pos.getX()+Math.min(0,dx),pos.getY(),pos.getZ()+Math.min(0,dz),
            pos.getX()+1+Math.max(0,dx),pos.getY()+LENGTH+1,pos.getZ()+1+Math.max(0,dz));
    }

    public BoomBarrierBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(POWERED, false));
    }
    public static BlockPos partPos(BlockPos origin, Direction facing, boolean vertical, int index) {
        return vertical ? origin.above(index) : origin.relative(facing, index);
    }
    private static boolean belongs(BlockState state, BlockPos pos, BlockPos origin) {
        return state.getBlock() instanceof BoomBarrierPartBlock && BoomBarrierPartBlock.masterPos(pos, state).equals(origin);
    }
    private static boolean room(Level level, BlockPos origin, Direction facing) {
        for (boolean vertical : new boolean[]{false, true}) for (int i = 1; i <= LENGTH; i++) {
            BlockPos pos = partPos(origin, facing, vertical, i);
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !belongs(state, pos, origin)) return false;
        }
        return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (!room(context.getLevel(), context.getClickedPos(), facing)) return null;
        Player player = context.getPlayer();
        if (player != null) for (int axis = 0; axis < 2; axis++) for (int i = 1; i <= LENGTH; i++) {
            BlockPos part = partPos(context.getClickedPos(), facing, axis == 1, i);
            if (!context.getLevel().mayInteract(player, part) || !player.mayUseItemAt(part, Direction.UP, context.getItemInHand())) return null;
        }
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(FACING, facing).setValue(OPEN, powered).setValue(POWERED, powered);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) syncParts(level, pos, state);
    }
    private static void syncParts(Level level, BlockPos origin, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (boolean vertical : new boolean[]{false, true}) for (int i = 1; i <= LENGTH; i++) {
            BlockPos pos = partPos(origin, facing, vertical, i);
            BlockState old = level.getBlockState(pos);
            if (old.isAir() || belongs(old, pos, origin)) {
                BlockState part = ModBlocks.BOOM_BARRIER_PART.get().defaultBlockState()
                    .setValue(BoomBarrierPartBlock.FACING, facing).setValue(BoomBarrierPartBlock.VERTICAL, vertical)
                    .setValue(BoomBarrierPartBlock.INDEX, i).setValue(OPEN, state.getValue(OPEN));
                if (old != part) level.setBlock(pos, part, Block.UPDATE_ALL);
            }
        }
    }
    private static boolean sweepClear(Level level, BlockPos origin, Direction facing) {
        // Check the quarter-circle swept by the moving arm; never overwrite another block.
        for (int distance = 0; distance <= LENGTH; distance++) for (int height = 0; height <= LENGTH; height++) {
            if (distance == 0 && height == 0 || distance * distance + height * height > LENGTH * LENGTH) continue;
            BlockPos pos = origin.relative(facing, distance).above(height);
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !belongs(state, pos, origin)) return false;
        }
        return true;
    }
    private static boolean change(Level level, BlockPos pos, BlockState state, boolean open) {
        if (open == state.getValue(OPEN)) return true;
        if (!sweepClear(level, pos, state.getValue(FACING))) return false;
        BlockState updated = state.setValue(OPEN, open);
        level.setBlockAndUpdate(pos, updated);
        syncParts(level, pos, updated);
        level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS, 0.6F, 0.85F);
        return true;
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player,pos)
                || !player.mayUseItemAt(pos,Direction.UP,player.getItemInHand(hand))) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            // Permission checks include every occupied arm cell, not just the motor.
            Direction facing = state.getValue(FACING);
            for (boolean vertical : new boolean[]{false, true}) for (int i = 1; i <= LENGTH; i++) {
                BlockPos part = partPos(pos, facing, vertical, i);
                if (!level.mayInteract(player, part) || !player.mayUseItemAt(part, Direction.UP, player.getItemInHand(hand)))
                    return InteractionResult.FAIL;
            }
            if (!change(level, pos, state, !state.getValue(OPEN))) {
                player.displayClientMessage(Component.translatable("block.trafficengine.boom_barrier.blocked"), true);
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            BlockState changed = state.setValue(POWERED, powered);
            level.setBlock(pos, changed, Block.UPDATE_CLIENTS);
            change(level, pos, changed, powered);
        }
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!room(level, pos, state.getValue(FACING))) { level.destroyBlock(pos, true); return; }
        syncParts(level, pos, state);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(this) && !level.isClientSide) {
            for (boolean vertical : new boolean[]{false, true}) for (int i = 1; i <= LENGTH; i++) {
                BlockPos part = partPos(pos, state.getValue(FACING), vertical, i);
                if (belongs(level.getBlockState(part), part, pos)) level.removeBlock(part, false);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(OPEN) ? BODY : CLOSED_BODY[state.getValue(FACING).get2DDataValue()]; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BoomBarrierBlockEntity(pos, state); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN, POWERED); }

    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state,level,pos,player,hand,hit));
    }
}
