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

/** A footing with an integrated pole, matching its inventory model. */
public class TrafficPoleBaseBlock extends Block implements SimpleWaterloggedBlock, ITrafficPostLike {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SINK = net.minecraft.world.level.block.state.properties.IntegerProperty.create("sink",0,8);
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape BASE = Block.box(4.5, 0, 4.5, 11.5, 5, 11.5);
    private static final VoxelShape MOUNTED = Shapes.or(BASE, Block.box(5.75, 5, 5.75, 10.25, 6.5, 10.25), Block.box(6.5, 6.5, 6.5, 9.5, 8, 9.5), Block.box(7, 8, 7, 9, 16, 9));
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    static {
        SHAPES[0]=MOUNTED;
        for(int sink=1;sink<=8;sink++) {
            double d=sink*2;
            VoxelShape shape=Shapes.or(Block.box(4.5,-d,4.5,11.5,5-d,11.5),Block.box(5.75,5-d,5.75,10.25,6.5-d,10.25),
                Block.box(6.5,6.5-d,6.5,9.5,8-d,9.5),Block.box(7,8-d,7,9,16,9)).optimize();
            SHAPES[sink]=shape;
        }
    }
    public static int sinkFor(BlockState below) {
        if((below.getBlock() instanceof AsphaltSlope || below.getBlock() instanceof AsphaltCurbSlope || below.getBlock() instanceof SidewalkSlopeBlock)
                && below.hasProperty(BlockStateProperties.LAYERS)) return 9-below.getValue(BlockStateProperties.LAYERS);
        return 0;
    }
    public TrafficPoleBaseBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(WATERLOGGED, false).setValue(SINK,0));
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        int terrainSink=sinkFor(level.getBlockState(pos.below()));
        if(terrainSink>0) {
            // The buried footing retains its carrier; wrench raises/embeds it without replacing the road.
            if(!level.isClientSide)level.setBlockAndUpdate(pos,state.setValue(SINK,state.getValue(SINK)==0?terrainSink:0));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
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
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state, level, pos, player, hand, hit));
    }
    static boolean hasPole(BlockState state) {
        if (state.getBlock() instanceof TrafficSignPostBlock) return state.getValue(TrafficSignPostBlock.AXIS) == Direction.Axis.Y;
        return state.getBlock() instanceof ITrafficPostLike post && post.canConnect(state, Direction.DOWN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(CONNECTED, hasPole(context.getLevel().getBlockState(context.getClickedPos().above())))
            .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER)
            .setValue(SINK,sinkFor(context.getLevel().getBlockState(context.getClickedPos().below())));
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if(side==Direction.DOWN)return state.setValue(SINK,sinkFor(neighbour));
        return side == Direction.UP ? state.setValue(CONNECTED, hasPole(neighbour)) : state;
    }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override public boolean canConnect(BlockState state, Direction side) { return side == Direction.UP; }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction side) { return side == Direction.UP; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[state.getValue(SINK)]; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(CONNECTED,WATERLOGGED,SINK); }
}
