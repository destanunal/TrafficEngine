package com.destan.trafficengine.block;

import net.minecraft.network.chat.Component;
import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.data.StreetLampShapes;
import com.destan.trafficengine.block.entity.StreetLampBlockEntity;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedstoneTorchBlock;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class StreetLampBaseBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, ITrafficPostLike, com.destan.trafficengine.block.data.IPaintableBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final VoxelShape SHAPE_COMMON = Block.box(7, 0, 7, 9, 11, 9);

    private LampType lampType;
    
    public StreetLampBaseBlock(LampType type) {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2f)
            .requiresCorrectToolForDrops()
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(LIT) ? 15 : 0)
        );

        this.lampType = type;
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(WATERLOGGED, false)
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false)
        );
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return SHAPE_COMMON;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction facing = pState.getValue(FACING);
        return switch (lampType) {
            case NORMAL -> StreetLampShapes.NORMAL.get(facing);
            case DOUBLE -> StreetLampShapes.DOUBLE.get(facing);
            case SMALL -> StreetLampShapes.SMALL.get(facing);
            case SMALL_DOUBLE -> StreetLampShapes.SMALL_DOUBLE.get(facing);
            case SINGLE_LIGHT -> StreetLampShapes.STREET_LIGHT.get(facing);
        };
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        onRemoveColor(state, level, pos, player);
    }

    @Override
    public InteractionResult onSetColor(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof StreetLampBlockEntity lamp) {
            if (!context.getLevel().isClientSide) {
                lamp.setColor(com.destan.trafficengine.item.BrushItem.getColor(context.getItemInHand()));
                context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.SLIME_BLOCK_PLACE,
                    SoundSource.BLOCKS, 0.8F, 2.0F);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack stack = pPlayer.getItemInHand(pHand);
        if (stack.getItem() instanceof net.minecraft.world.item.DyeItem dye
                && pLevel.getBlockEntity(pPos) instanceof StreetLampBlockEntity lamp) {
            com.destan.trafficengine.data.PaintColor color = com.destan.trafficengine.data.PaintColor.getByDye(dye);
            if (lamp.getColor() == color) return InteractionResult.SUCCESS;
            if (!pLevel.isClientSide) {
                lamp.setColor(color);
                if (!pPlayer.isCreative()) stack.shrink(1);
                pLevel.playSound(null, pPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 2.0F);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        if (stack.is(ModItemTags.WRENCHES)) {
            if (!pLevel.isClientSide) {
                if (pLevel.getBlockEntity(pPos) instanceof StreetLampBlockEntity blockEntity && blockEntity.getOnTime() != blockEntity.getOffTime()) {
                    if (!pLevel.isClientSide) {
                        pPlayer.displayClientMessage(Component.translatable("block.trafficengine.street_lamp.use.error_scheduled"), true);
                        return InteractionResult.FAIL;
                    }
                } else {                    
                    pLevel.setBlockAndUpdate(pPos, pState.setValue(LIT, !pState.getValue(LIT)));
                }
            } else {            
                pLevel.playSound(pPlayer, pPos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, 0.5f);
            }
            return InteractionResult.SUCCESS;
        }  

        
        
        return InteractionResult.PASS;
    }

    
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        if (pState.getValue(WATERLOGGED)) {
           pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }
  
        return super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
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

        return this.defaultBlockState()
            .setValue(FACING, pContext.getHorizontalDirection().getOpposite())
            .setValue(WATERLOGGED, Boolean.valueOf(flag))
            .setValue(LIT, false);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, LIT, WATERLOGGED);
    }

    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
    }   

    public enum LampType {
        NORMAL,
        SMALL,
        DOUBLE,
        SMALL_DOUBLE,
        SINGLE_LIGHT
    }

    /* BLOCK ENTITY */
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new StreetLampBlockEntity(pPos, pState);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (!pLevel.isClientSide) {
            return (level, pos, state, blockEntity) -> {
                long tickCount = level.getGameTime();
                if (tickCount % 50 == 0) {
                    ((StreetLampBlockEntity)blockEntity).tick(level, pos, state);
                }
            };
        }
        return null;
    }

    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        return false;
    }  

    @Override
    public boolean canConnect(BlockState pState, Direction pDirection) {
        return pDirection == Direction.DOWN;
    }
}
