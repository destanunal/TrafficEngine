package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.IPaintableBlock;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class DelineatorBlock extends WaterloggableBlock implements IPaintableBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DyeColor> REFLECTOR_COLOR = EnumProperty.create("reflector_color", DyeColor.class);
    
    public static final VoxelShape SHAPE_COMMON = Block.box(6, 0, 6, 10, 16, 10);
    public static final VoxelShape SHAPE_SMALL = Block.box(6, 0, 6, 10, 11, 10);
    
    public static final VoxelShape OCCLUSION_COMMON = Block.box(5, 0, 5, 9, 16, 9);
    public static final VoxelShape OCCLUSION_SMALL = Block.box(5, 0, 5, 9, 11, 9);

    private final boolean small;
    
    public DelineatorBlock(boolean small) {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .sound(SoundType.BAMBOO)   
            .instabreak()         
        );
        this.small = small;
        BlockState initial = stateDefinition.any().setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH);
        registerDefaultState(initial.hasProperty(REFLECTOR_COLOR) ? initial.setValue(REFLECTOR_COLOR, DyeColor.WHITE) : initial);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return isSmall() ? OCCLUSION_SMALL : OCCLUSION_COMMON;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {  
        return isSmall() ? SHAPE_SMALL : SHAPE_COMMON;
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

        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, Boolean.valueOf(flag));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING);
        addReflectorColor(pBuilder);
    }

    protected void addReflectorColor(Builder<Block, BlockState> builder) {
        builder.add(REFLECTOR_COLOR);
    }

    public static int reflectorTint(BlockState state, int index) {
        if (index != 0) return 0xFFFFFFFF;
        DyeColor dye = state.getValue(REFLECTOR_COLOR);
        return dye == DyeColor.WHITE ? 0xFFFFFFFF : PaintColor.getByIndex(dye.getId()).getTextureARGB();
    }

    private BlockState paintedState(BlockState current, DyeColor dye) {
        if (current.hasProperty(REFLECTOR_COLOR)) return current.setValue(REFLECTOR_COLOR, dye);
        Block canonical = small ? ModBlocks.SMALL_WHITE_DELINEATOR.get() : ModBlocks.WHITE_DELINEATOR.get();
        return canonical.defaultBlockState().setValue(FACING, current.getValue(FACING))
            .setValue(WATERLOGGED, current.getValue(WATERLOGGED)).setValue(REFLECTOR_COLOR, dye);
    }

    private boolean paint(Level level, BlockPos pos, BlockState current, DyeColor dye) {
        BlockState next = paintedState(current, dye);
        if (next == current) return false;
        if (level.isClientSide) return true;
        if (!level.setBlockAndUpdate(pos, next)) return false;
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, .8F, 1F);
        return true;
    }

    @Override
    public InteractionResult onSetColor(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        PaintColor color = BrushItem.getColor(context.getItemInHand());
        DyeColor dye = color == PaintColor.NONE ? DyeColor.WHITE : DyeColor.byId(color.getIndex());
        return paint(level, pos, level.getBlockState(pos), dye) ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    @Override
    public void onRemoveColor(BlockState state, Level level, BlockPos pos, Player player) {
        paint(level, pos, state, DyeColor.WHITE);
    }

    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
    }

    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        return pFacing == Direction.DOWN && !this.canSurvive(pState, pLevel, pCurrentPos) ? Blocks.AIR.defaultBlockState() : super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return canSupportCenter(pLevel, pPos.below(), Direction.UP);
    }

    public boolean isSmall() {
        return small;
    }

    /** Saved identifiers keep their original blockstates until repainted. */
    public static final class Legacy extends DelineatorBlock {
        public Legacy(boolean small) {
            super(small);
        }

        @Override
        protected void addReflectorColor(Builder<Block, BlockState> builder) {}
    }
    
}
