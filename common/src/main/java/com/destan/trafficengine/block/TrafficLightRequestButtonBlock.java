package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.entity.TrafficLightRequestButtonBlockEntity;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class TrafficLightRequestButtonBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, ITrafficPostLike {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty LOWER_POLE = BooleanProperty.create("lower_pole");

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SINK = TrafficPoleBaseBlock.SINK;
    private static int supportSink(BlockState below, BlockGetter world, BlockPos pos) {
        if (below.getBlock() instanceof TrafficPoleBaseBlock) return below.getValue(TrafficPoleBaseBlock.SINK);
        if (below.getBlock() instanceof TrafficSignPostBlock && below.getValue(TrafficSignPostBlock.AXIS) == Direction.Axis.Y)
            return RailingGeometry.sinkFor(world.getBlockState(pos.below(2)));
        return RailingGeometry.sinkFor(below);
    }
    /** Full-height carriers already own the centre stem below the button's block. */
    public static boolean hasLowerPole(BlockState below) {
        return below.getBlock() instanceof TrafficSignPostBlock && below.getValue(TrafficSignPostBlock.AXIS) == Direction.Axis.Y
                || below.getBlock() instanceof TrafficPoleBaseBlock
                || below.getBlock() instanceof TrafficLightRequestButtonBlock;
    }
    public static BlockState supportedState(BlockState state, BlockGetter world, BlockPos pos) {
        BlockState below = world.getBlockState(pos.below());
        return state.setValue(SINK, supportSink(below, world, pos)).setValue(LOWER_POLE, hasLowerPole(below));
    }
    private static final VoxelShape[][][] SUNK_SHAPES = new VoxelShape[9][4][2];
    private static final int PRESS_DURATION = 30;

    public static final VoxelShape SHAPE_COMMON = Block.box(7, 0, 7, 9, 16, 9);

    public static final VoxelShape SHAPE_NORTH = Shapes.or(Block.box(5.5, 1, 3.5, 10.5, 15, 9.22), SHAPE_COMMON);
    public static final VoxelShape SHAPE_SOUTH = Shapes.or(Block.box(5.5, 1, 6.78, 10.5, 15, 12.5), SHAPE_COMMON);
    public static final VoxelShape SHAPE_EAST = Shapes.or(Block.box(6.78, 1, 5.5, 12.5, 15, 10.5), SHAPE_COMMON);
    public static final VoxelShape SHAPE_WEST = Shapes.or(Block.box(3.5, 1, 5.5, 9.22, 15, 10.5), SHAPE_COMMON);
    
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape=switch(facing) { case NORTH -> SHAPE_NORTH; case SOUTH -> SHAPE_SOUTH; case EAST -> SHAPE_EAST; default -> SHAPE_WEST; };
            // Keep the button housing lowered, but never duplicate a lower carrier's stem.
            VoxelShape housing=switch(facing) {
                case NORTH -> Block.box(5.5,1,3.5,10.5,15,9.22);
                case SOUTH -> Block.box(5.5,1,6.78,10.5,15,12.5);
                case EAST -> Block.box(6.78,1,5.5,12.5,15,10.5);
                default -> Block.box(3.5,1,5.5,9.22,15,10.5);
            };
            for(int sink=0;sink<=8;sink++) {
                SUNK_SHAPES[sink][facing.get2DDataValue()][0]=Shapes.or(shape.move(0,-sink/8D,0),SHAPE_COMMON);
                SUNK_SHAPES[sink][facing.get2DDataValue()][1]=Shapes.or(housing.move(0,-sink/8D,0),SHAPE_COMMON);
            }
        }
    }

    public TrafficLightRequestButtonBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2f)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)            
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(SINK, 0)
            .setValue(LOWER_POLE, false)
            .setValue(FACING, Direction.NORTH)       
            .setValue(POWERED, false)
            .setValue(ACTIVATED, false)              
            .setValue(WATERLOGGED, false)
        );        
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        int sink=pState.getValue(SINK), facing=pState.getValue(FACING).get2DDataValue();
        return SUNK_SHAPES[sink][facing][pState.getValue(LOWER_POLE) ? 1 : 0];
    }

    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pResult) {
        // Allow BlockItem placement to reach the upper pole face without pressing the button.
        if (pPlayer.getItemInHand(pHand).getItem() instanceof net.minecraft.world.item.BlockItem)
            return InteractionResult.PASS;
        if (!pState.getValue(POWERED) && !pState.getValue(ACTIVATED)) {
            Direction direction = pResult.getDirection();
            BlockPos blockpos = pResult.getBlockPos();
            boolean flag = this.isProperHit(pState, direction, pResult.getLocation().y - (double)blockpos.getY() + pState.getValue(SINK)/8D);
            if (flag) {
                this.press(pState, pLevel, pPos);
                pLevel.gameEvent(pPlayer, GameEvent.BLOCK_ACTIVATE, pPos);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.FAIL;
    }

    private boolean isProperHit(BlockState state, Direction pDirection, double pDistanceY) {
        if (pDirection ==  state.getValue(FACING) && !(pDistanceY > (double)0.9375F) && !(pDistanceY < (double)0.0625F)) {
            return true;
        } else {
           return false;
        }
    }

    public void press(BlockState pState, Level pLevel, BlockPos pPos) {
        pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, Boolean.valueOf(true)));
        pLevel.playSound(null, pPos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.90000004F);
        pLevel.scheduleTick(pPos, this, PRESS_DURATION);
    }
    
    @Override
    public void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRand) {
        if (pState.getValue(POWERED)) {
            if (pLevel.getBlockEntity(pPos) instanceof TrafficLightRequestButtonBlockEntity blockEntity && blockEntity.isValidLinked()) {
                if (blockEntity.activate()) {
                    pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, false).setValue(ACTIVATED, true));
                } else {
                    pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, false).setValue(ACTIVATED, false));
                }
            } else {
                pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, false).setValue(ACTIVATED, false));
            }

            pLevel.playSound(null, pPos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundSource.BLOCKS, 0.3F, 0.75F);
            pLevel.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pPos);              
        }
    }


    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        if (pState.getValue(WATERLOGGED)) {
           pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }
  
        return pFacing == Direction.DOWN ? supportedState(pState,pLevel,pCurrentPos) : pState;
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState pState) {
        return pState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(pState);
    }
    
    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }    

    @Override
    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        FluidState fluidstate = pContext.getLevel().getFluidState(pContext.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;

        return supportedState(this.defaultBlockState(),pContext.getLevel(),pContext.getClickedPos()).setValue(FACING, pContext.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, Boolean.valueOf(flag));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, POWERED, ACTIVATED, WATERLOGGED, SINK, LOWER_POLE);
    }

    public boolean isPathfindable(BlockState pState, PathComputationType pType) {
        return false;
    } 

    
    
    /* BLOCK ENTITY */
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrafficLightRequestButtonBlockEntity(pPos, pState);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide) return null;
        return createTickerHelper(pBlockEntityType, ModBlockEntities.TRAFFIC_LIGHT_REQUEST_BUTTON_BLOCK_ENTITY.get(), TrafficLightRequestButtonBlockEntity::tick);
    }

    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        return pDirection != pState.getValue(FACING);
    }
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state, level, pos, player, hand, hit));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

}
