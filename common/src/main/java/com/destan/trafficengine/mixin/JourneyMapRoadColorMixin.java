package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * blocks. It does not change the normal block model or its in-world colour.
 */
@Pseudo
@Mixin(targets = "journeymap.client.mod.vanilla.VanillaBlockColorProxy", remap = false)
public abstract class JourneyMapRoadColorMixin {

    @Inject(method = "getBlockColor", at = @At("RETURN"), cancellable = true,
            require = 0, remap = false)
    private void trafficengine$useRoadMarkingColor(@Coerce Object chunk,
            @Coerce Object blockMetadata, BlockPos pos,
            CallbackInfoReturnable<Integer> callback) {
        ColoredBlockEntity blockEntity = trafficengine$getRoadBlockEntity(chunk, pos);
        if (blockEntity == null || !(blockEntity.getBlockState().getBlock() instanceof RoadBlock)) {
            return;
        }

        PaintColor markingColor = blockEntity.getMarkingColor();
        PaintColor mapColor = markingColor != PaintColor.NONE
                ? markingColor
                : blockEntity.getColor();
        if (mapColor != PaintColor.NONE) {
            // The primary colour fallback is needed by road markings saved by
            // older versions before the separate marking_colour field existed.
            callback.setReturnValue(mapColor.getTextureColor().getAsARGB() & 0xFFFFFF);
        }
    }

    private static ColoredBlockEntity trafficengine$getRoadBlockEntity(Object chunkMetadata, BlockPos pos) {
        // JourneyMap may retain the source chunk after it leaves Minecraft's
        // active render distance. Prefer that chunk so old/rebuilt map regions
        // receive the same colour correction as nearby ones.
        try {
            Object chunk = chunkMetadata.getClass().getMethod("getChunk").invoke(chunkMetadata);
            if (chunk instanceof LevelChunk levelChunk
                    && levelChunk.getBlockEntity(pos) instanceof ColoredBlockEntity blockEntity) {
                return blockEntity;
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep JourneyMap optional and compatible with versions where its
            // internal chunk wrapper differs.
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof ColoredBlockEntity blockEntity) {
            return blockEntity;
        }
        return null;
    }
}
