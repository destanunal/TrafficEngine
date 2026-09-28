package com.destan.trafficengine.block.data;

import com.destan.trafficengine.block.PaintedAsphaltBlock;
import com.destan.trafficengine.block.PaintedAsphaltSlope;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public abstract class RoadBlock extends ColorableBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty BASE_PAINTED = BooleanProperty.create("base_painted");

    private RoadType defaultRoadType;

    public RoadBlock(Properties properties, RoadType type) {
        super(properties);
        this.defaultRoadType = type;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BASE_PAINTED, false));
    }

    public RoadType getDefaultRoadType() {
        return this.defaultRoadType;
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
        return this.defaultBlockState()
                .setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, BASE_PAINTED);
    }

    @Override
    public InteractionResult onSetColor(UseOnContext pContext) {
        String id = "";
        Level level = pContext.getLevel();
        BlockPos pos = pContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = pContext.getItemInHand();
        Player player = pContext.getPlayer();
        
        if (level.getBlockEntity(pos) instanceof IColorBlockEntity) {
            PaintColor baseColor = PaintColor.NONE;
            if (level.getBlockEntity(pos) instanceof ColoredBlockEntity coloredBlockEntity) {
                baseColor = coloredBlockEntity.getColor();
            }

            boolean basePainted = state.getValue(BASE_PAINTED);
            int patternId = BrushItem.getPatternId(stack);
            // Pattern 0 used to store full-surface paint as the single legacy
            // road colour. Promote it to the new base colour before applying
            // any pattern so every marking (bike, pedestrian, arrows, lines,
            // etc.) can be layered on top without losing the old paint.
            if (!basePainted && isLegacyFullPaint(state)) {
                if (level.getBlockEntity(pos) instanceof ColoredBlockEntity coloredBlockEntity) {
                    PaintColor legacyColor = coloredBlockEntity.getMarkingColor();
                    if (legacyColor == PaintColor.NONE) {
                        legacyColor = coloredBlockEntity.getColor();
                    }
                    if (legacyColor != PaintColor.NONE) {
                        baseColor = legacyColor;
                        basePainted = true;
                    }
                }
            }
            // Pattern 0 is the plain road-paint mode: it changes only the
            // road surface. All other patterns retain that surface colour.
            if (patternId == 0) {
                baseColor = BrushItem.getColor(stack);
                basePainted = true;
            }

            if (state.getBlock() instanceof PaintedAsphaltBlock)
                id = this.getDefaultRoadType().getRoadType() + "_pattern_" + patternId;
            else if (state.getBlock() instanceof PaintedAsphaltSlope)
                id = this.getDefaultRoadType().getRoadType() + "_slope_pattern_" + patternId;

            if (!ModBlocks.ROAD_BLOCKS.containsKey(id)) {
                return InteractionResult.FAIL;
            }

            BlockState newState = ModBlocks.ROAD_BLOCKS.get(id).get().defaultBlockState()
                    .setValue(RoadBlock.FACING, player.getDirection())
                    .setValue(RoadBlock.BASE_PAINTED, basePainted);
                if (state.getBlock() instanceof PaintedAsphaltSlope) {
                    newState = newState.setValue(PaintedAsphaltSlope.LAYERS, state.getValue(PaintedAsphaltSlope.LAYERS));
                }

                level.setBlockAndUpdate(pos, newState);
                if (level.getBlockEntity(pos) instanceof ColoredBlockEntity coloredBlockEntity) {
                    PaintColor markingColor = patternId == 0 ? PaintColor.NONE : BrushItem.getColor(stack);
                    coloredBlockEntity.setRoadColors(baseColor, markingColor);
                }
                if (!level.isClientSide) {
                    level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 2.0F);
                }

                return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }

    private static boolean isLegacyFullPaint(BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key.getPath();
        return path.endsWith("_pattern_0") || path.endsWith("_slope_pattern_0");
    }

    @Override
    public InteractionResult update(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos pos = pContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = pContext.getItemInHand();

        String id = "";
        if (state.getBlock() instanceof PaintedAsphaltBlock)
            id = this.getDefaultRoadType().getRoadType() + "_pattern_" + BrushItem.getPatternId(stack);
        else if (state.getBlock() instanceof PaintedAsphaltSlope)
            id = this.getDefaultRoadType().getRoadType() + "_slope_pattern_" + BrushItem.getPatternId(stack);

        if (ModBlocks.ROAD_BLOCKS.containsKey(id) && state.getBlock() != ModBlocks.ROAD_BLOCKS.get(id).get()) {
            return this.onSetColor(pContext);
        }

        level.setBlockAndUpdate(pos, state.setValue(RoadBlock.FACING, state.getValue(RoadBlock.FACING).getClockWise(Axis.Y)));
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 2.0F);

        return InteractionResult.SUCCESS;
    }
}
