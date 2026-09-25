package com.destan.trafficengine.client;

import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.IPaintableBlock;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.item.ColorPaletteItem;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class TintedTextures {

    public static class TintedBlock implements BlockColor {
        @Override
        public int getColor(BlockState pState, BlockAndTintGetter pLevel, BlockPos pPos, int pTintIndex) {
            
            if (pState.getBlock() instanceof IPaintableBlock block) {
                if (pLevel == null) {
                    return block.getDefaultColor().getAsARGB();
                }

                if (pLevel.getBlockEntity(pPos) instanceof IColorBlockEntity blockEntity) {
                    if (pState.getBlock() instanceof RoadBlock roadBlock && pTintIndex > 1
                            && blockEntity instanceof ColoredBlockEntity coloredBlockEntity) {
                        // Map renderers such as JourneyMap request one colour for the
                        // complete MODEL render shape instead of querying the model's
                        // individual tint indices (0 = base, 1 = marking). Prefer the
                        // marking so lane lines and road symbols remain visible there;
                        // plain/full-painted roads still fall back to their base colour.
                        PaintColor markingColor = coloredBlockEntity.getMarkingColor();
                        if (markingColor != PaintColor.NONE) {
                            return markingColor.getTextureColor().getAsARGB();
                        }

                        PaintColor baseColor = blockEntity.getColor();
                        return baseColor == PaintColor.NONE
                                ? roadBlock.getDefaultRoadType().getColor()
                                : baseColor.getTextureColor().getAsARGB();
                    }
                    if (pState.getBlock() instanceof RoadBlock && pTintIndex == 1
                            && blockEntity instanceof ColoredBlockEntity coloredBlockEntity) {
                        PaintColor markingColor = coloredBlockEntity.getMarkingColor();
                        if (markingColor != PaintColor.NONE) {
                            return markingColor.getTextureColor().getAsARGB();
                        }

                        // Full paint and legacy road markings may keep their
                        // original colour in the primary colour field. Do not
                        // replace that saved colour with white merely because
                        // the newer, separate marking field is empty.
                        PaintColor legacyColor = blockEntity.getColor();
                        return legacyColor == PaintColor.NONE
                                ? 0xFFFFFFFF
                                : legacyColor.getTextureColor().getAsARGB();
                    }
                    PaintColor c = blockEntity.getColor();
                    if (pState.getBlock() instanceof RoadBlock roadBlock && pTintIndex == 0
                            && c == PaintColor.NONE) {
                        // Original pattern-0 blocks stored their full-paint
                        // colour in the legacy marking field in some worlds.
                        // Keep those existing blocks visually identical.
                        if (isFullPaintBlock(pState)
                                && blockEntity instanceof ColoredBlockEntity coloredBlockEntity
                                && coloredBlockEntity.getMarkingColor() != PaintColor.NONE) {
                            return coloredBlockEntity.getMarkingColor().getTextureColor().getAsARGB();
                        }
                        return roadBlock.getDefaultRoadType().getColor();
                    }
                    return c == PaintColor.NONE ? block.getDefaultColor().getAsARGB() : c.getTextureColor().getAsARGB();
                }
            }
                        
            return 0;
        }

        private static boolean isFullPaintBlock(BlockState state) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return path.endsWith("_pattern_0") || path.endsWith("_slope_pattern_0");
        }
    }

    public static class TintedItem implements ItemColor {
        @Override
        public int getColor(ItemStack pStack, int pTintIndex) {

            if (pStack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() instanceof IPaintableBlock coloredBlock) {
                    return coloredBlock.getDefaultColor().getAsARGB();
                }
            } else if (pStack.getItem() instanceof BrushItem) {                
                if (pTintIndex == 1) {
                    return BrushItem.getColor(pStack).getTextureColor().getAsARGB();
                } else {
                    return 0xFFFFFFFF;
                }
            } else if (pStack.getItem() instanceof ColorPaletteItem) {
                if (pTintIndex == 0) {
                    return 0xFFFFFFFF;
                }
                int color = ColorPaletteItem.getColorAt(pStack, pTintIndex - 1);
                if (color == 0) {
                    color = 0xFFFFFFFF;
                }
                return color;
            }
            return 0;
        }
        
    }
    
}
