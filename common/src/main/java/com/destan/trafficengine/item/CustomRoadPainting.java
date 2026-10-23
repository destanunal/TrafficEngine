package com.destan.trafficengine.item;

import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.data.PixelPattern;
import com.destan.trafficengine.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** A stencil is baked into the existing road chunk mesh, including partial-height roads. */
public final class CustomRoadPainting {
    private CustomRoadPainting() {}
    public static InteractionResult use(UseOnContext context, PixelPattern pattern) {
        var level = context.getLevel(); var player = context.getPlayer(); var pos = context.getClickedPos();
        var stack = context.getItemInHand(); var old = level.getBlockState(pos);
        if (player == null || !RoadPainting.isRoad(old) || context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof ColoredBlockEntity colored && !colored.getCustomRoadPattern().isEmpty()) {
            if (!level.isClientSide) {
                stack.getOrCreateTag().putLongArray(ColoredBlockEntity.CUSTOM_ROAD_PATTERN, colored.getCustomRoadPattern().bits());
                stack.getOrCreateTag().putBoolean("custom_road_enabled", true);
                stack.getOrCreateTag().putInt(RoadPainting.STYLE, -1); stack.getOrCreateTag().putInt(RoadPainting.AREA_STYLE, 0);
                stack.getOrCreateTag().putBoolean(RoadPainting.BATCH, false); RoadPainting.clearSelection(stack.getOrCreateTag());
                stack.getOrCreateTag().putInt(BrushItem.NBT_COLOR, colored.getMarkingColor().getIndex());
                player.getInventory().setChanged(); player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.copied"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown() || pattern.isEmpty()) return InteractionResult.PASS;
        if (!player.isCreative() && BrushItem.getPaint(stack) < 1) return InteractionResult.FAIL;
        boolean slope = old.getBlock() instanceof AsphaltSlope || old.getBlock() instanceof PaintedAsphaltSlope;
        var type = old.getBlock() instanceof RoadBlock road ? road.getDefaultRoadType() : ((AsphaltBlock)old.getBlock()).getDefaultRoadType();
        var target = (slope ? ModBlocks.CONNECTED_SLOPES : ModBlocks.CONNECTED_ROADS).get(type);
        if (target == null) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        
        PaintColor base = PaintColor.NONE;
        boolean painted = old.hasProperty(RoadBlock.BASE_PAINTED) && old.getValue(RoadBlock.BASE_PAINTED);
        if (level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
            base = colored.getColor();
            if (!painted && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath().endsWith("_pattern_0")) {
                if (colored.getMarkingColor() != PaintColor.NONE) base = colored.getMarkingColor();
                painted = base != PaintColor.NONE;
            }
        }
        var state = target.get().defaultBlockState().setValue(RoadBlock.FACING, player.getDirection()).setValue(RoadBlock.BASE_PAINTED, painted);
        if (slope) state = state.setValue(PaintedAsphaltSlope.LAYERS, old.getValue(PaintedAsphaltSlope.LAYERS)).setValue(PaintedAsphaltSlope.WATERLOGGED, old.getValue(PaintedAsphaltSlope.WATERLOGGED));
        else state = ConnectedRoadBlock.reconnect(state.setValue(ConnectedRoadBlock.STROKE, true), level, pos);
        if (state.equals(old) && level.getBlockEntity(pos) instanceof ColoredBlockEntity existing
                && existing.getCustomRoadPattern().equals(pattern) && existing.getMarkingColor() == BrushItem.getColor(stack)) return InteractionResult.CONSUME;
        var undo = RoadPaintUndo.begin(level, player, stack, List.of(pos));
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
            colored.setRoadColors(base, BrushItem.getColor(stack)); colored.setCustomRoadPattern(pattern);
        }
        if (!player.isCreative()) stack.getOrCreateTag().putInt(BrushItem.NBT_PAINT, BrushItem.getPaint(stack) - 1);
        RoadPaintUndo.finish(undo); player.getInventory().setChanged();
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, .8F, 2);
        return InteractionResult.CONSUME;
    }
}
