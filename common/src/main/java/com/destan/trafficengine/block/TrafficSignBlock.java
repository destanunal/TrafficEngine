package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.block.entity.TrafficSignBlockEntity;
import com.destan.trafficengine.data.TrafficSignTextureData;
import com.destan.trafficengine.data.TrafficSignTextureManager;
import com.destan.trafficengine.item.CreativePatternCatalogueItem;
import com.destan.trafficengine.item.PatternCatalogueItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrafficSignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, ITrafficPostLike {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    public static final EnumProperty<TrafficSignShape> SHAPE = EnumProperty.create("shape", TrafficSignShape.class);

    public TrafficSignBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .sound(SoundType.LANTERN)
        );

        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            .setValue(WALL_MOUNTED, false)
            .setValue(SHAPE, TrafficSignShape.SQUARE)
        );
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        if (pState.getValue(WALL_MOUNTED)) {
            return getWallMountedShape(pState.getValue(SHAPE), pState.getValue(FACING));
        }
        return pState.getValue(SHAPE).getVoxelShape(pState.getValue(FACING));
    }

    private static VoxelShape getWallMountedShape(TrafficSignShape shape, Direction facing) {
        double minX = 0;
        double maxX = 16;
        double minY = 0;
        double maxY = 16;

        switch (shape) {
            case RECTANGLE -> {
                minX = 2;
                maxX = 14;
            }
            case RECTANGLE_SMALL -> {
                minX = 4;
                maxX = 12;
            }
            case RECTANGLE_HORIZONTAL -> minY = 4;
            case SMALL_UPPER -> {
                minX = 1;
                maxX = 15;
                minY = 8;
            }
            case SMALL_LOWER -> {
                minX = 1;
                maxX = 15;
                maxY = 8;
            }
            default -> {
            }
        }

        return switch (facing) {
            case NORTH -> Block.box(minX, minY, 15.5, maxX, maxY, 16);
            case SOUTH -> Block.box(16 - maxX, minY, 0, 16 - minX, maxY, 0.5);
            case EAST -> Block.box(0, minY, minX, 0.5, maxY, maxX);
            case WEST -> Block.box(15.5, minY, 16 - maxX, 16, maxY, 16 - minX);
            default -> Block.box(0, minY, 15.5, 16, maxY, 16);
        };
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack stack = pPlayer.getInventory().getSelected();
        Item item = stack.getItem();

        if (item instanceof PatternCatalogueItem && ((item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack)) || PatternCatalogueItem.getSelectedPattern(stack) != null)) {
            if (pLevel.getBlockEntity(pPos) instanceof TrafficSignBlockEntity blockEntity) {
                if (item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack)) {
                    blockEntity.setAndResetTexture(CreativePatternCatalogueItem.getCustomImage(stack));
                } else {
                    blockEntity.setAndResetTexture(PatternCatalogueItem.getSelectedPattern(stack));
                }
            }
            
            if (pLevel.isClientSide) {
                pLevel.playSound(pPlayer, pPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.3F, 1.5f);
            } else {
                TrafficSignTextureData data = TrafficSignTextureManager.load(item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack) ? CreativePatternCatalogueItem.getCustomImage(stack).getTextureId() : PatternCatalogueItem.getSelectedPattern(stack).getTextureId());
                pLevel.setBlockAndUpdate(pPos, pState.setValue(SHAPE, data.getShape()));
            }
            
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
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
        Direction clickedFace = pContext.getClickedFace();
        boolean wallMounted = false;

        // Only the regular traffic sign changes mode automatically. Subclasses
        // such as the double-sided and legacy wall sign keep their own behavior.
        if (this.getClass() == TrafficSignBlock.class && clickedFace.getAxis().isHorizontal()) {
            BlockPos supportPos = pContext.getClickedPos().relative(clickedFace.getOpposite());
            BlockState supportState = pContext.getLevel().getBlockState(supportPos);
            wallMounted = supportState.isFaceSturdy(pContext.getLevel(), supportPos, clickedFace);
        }

        Direction facing = wallMounted ? clickedFace : pContext.getHorizontalDirection().getOpposite();
        return this.defaultBlockState()
            .setValue(FACING, facing)
            .setValue(WATERLOGGED, flag)
            .setValue(WALL_MOUNTED, wallMounted);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, WATERLOGGED, WALL_MOUNTED, SHAPE);
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        if (!pState.getValue(WALL_MOUNTED)) {
            return super.canSurvive(pState, pLevel, pPos);
        }

        Direction facing = pState.getValue(FACING);
        BlockPos supportPos = pPos.relative(facing.getOpposite());
        return pLevel.getBlockState(supportPos).isFaceSturdy(pLevel, supportPos, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        if (pState.getValue(WATERLOGGED)) {
           pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }

        if (pState.getValue(WALL_MOUNTED)
            && pFacing == pState.getValue(FACING).getOpposite()
            && !pState.canSurvive(pLevel, pCurrentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
  
        return super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState pState) {
        return pState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(pState);
    }

    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
    }

    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        return !pState.getValue(WALL_MOUNTED)
            && pState.getValue(SHAPE) != TrafficSignShape.SMALL_LOWER
            && pState.getValue(FACING).getOpposite() == pDirection;
    }

    /* BLOCK ENTITY */
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrafficSignBlockEntity(pPos, pState);
    }
}
