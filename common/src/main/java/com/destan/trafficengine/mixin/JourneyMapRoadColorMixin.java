package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.ConnectedRoadBlock;
import com.destan.trafficengine.block.ConnectedRoadSlopeBlock;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.client.TintedTextures;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JourneyMap averages every sprite in a multipart road model before applying
 * its block tint. For road markings that mixes the dark asphalt sprite into
 * the white/yellow overlay and makes thin lines almost invisible on the map.
 *
 * This optional mixin replaces only JourneyMap's final colour for marked road
 * blocks. Persistent brush meshes use their painted coverage, keeping hatch
 * gaps dark rather than turning the entire area into solid paint. This does
 * not change the normal block model or its in-world colour.
 */
@Pseudo
@Mixin(targets = "journeymap.client.mod.vanilla.VanillaBlockColorProxy", remap = false)
public abstract class JourneyMapRoadColorMixin {

    @Inject(method = "getBlockColor", at = @At("RETURN"), cancellable = true,
            require = 0, remap = false)
    private void trafficengine$useRoadMarkingColor(@Coerce Object chunk,
            @Coerce Object blockMetadata, BlockPos pos,
            CallbackInfoReturnable<Integer> callback) {
        ColoredBlockEntity blockEntity = com.destan.trafficengine.client.compat.JourneyMapRoadData.road(chunk,pos);
        if (blockEntity == null || !(blockEntity.getBlockState().getBlock() instanceof RoadBlock roadBlock)) {
            return;
        }

        PaintColor markingColor = blockEntity.getMarkingColor();
        var state = blockEntity.getBlockState();
        if (state.getBlock() instanceof ConnectedRoadSlopeBlock
                || state.getBlock() instanceof ConnectedRoadBlock) {
            PaintColor baseColor = blockEntity.getColor();
            int background = baseColor == PaintColor.NONE ? roadBlock.getDefaultRoadType().getColor()
                    : baseColor.getTextureColor().getAsARGB();
            int paint = markingColor == PaintColor.NONE ? background : markingColor.getTextureColor().getAsARGB();
            // Reuse saved brush geometry; do not scan/tessellate adjacent roads on a map worker.
            float[] mesh=blockEntity.getStrokeVertices();
            int style=state.getValue(ConnectedRoadBlock.STYLE).ordinal();
            int mapColor=mesh.length>0?TintedTextures.strokeMapColor(background,paint,mesh)
                    :TintedTextures.strokeMapColor(background,paint,style==0?.125:style==2?.5:.25);
            callback.setReturnValue(mapColor & 0xFFFFFF);
            return;
        }
        PaintColor mapColor = markingColor != PaintColor.NONE
                ? markingColor
                : blockEntity.getColor();
        if (mapColor != PaintColor.NONE) {
            // The primary colour fallback is needed by road markings saved by
            // older versions before the separate marking_colour field existed.
            callback.setReturnValue(mapColor.getTextureColor().getAsARGB() & 0xFFFFFF);
        }
    }

}
