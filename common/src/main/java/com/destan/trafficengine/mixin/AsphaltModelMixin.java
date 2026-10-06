package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.PaintedAsphaltBlock;
import com.destan.trafficengine.block.PaintedAsphaltSlope;
import com.destan.trafficengine.block.AsphaltSlope;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.client.model.PreservedAsphaltModel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockModelShaper.class)
public abstract class AsphaltModelMixin {
    @ModifyVariable(method = "replaceCache", at = @At("HEAD"), argsOnly = true)
    private Map<BlockState, BakedModel> trafficengine$preserveAsphalt(Map<BlockState, BakedModel> models) {
        Map<BlockState, BakedModel> result = new HashMap<>(models);
        Map<PreservedAsphaltModel.CacheKey, BakedModel> wrappers = new HashMap<>();
        Map<RoadType, Map<BakedQuad, BakedQuad>> paintedQuads = new HashMap<>();
        BlockModelShaper shaper = (BlockModelShaper) (Object) this;
        for (var entry : models.entrySet()) {
            BlockState state = entry.getKey();
            boolean slope = state.getBlock() instanceof PaintedAsphaltSlope;
            if (!(state.getBlock() instanceof PaintedAsphaltBlock) && !slope) continue;
            RoadBlock road = (RoadBlock) state.getBlock();
            RoadType type = road.getDefaultRoadType();
            if (type == RoadType.NONE || type == RoadType.CONCRETE) continue;
            BlockState baseState = slope
                    ? type.getSlope().defaultBlockState()
                            .setValue(AsphaltSlope.LAYERS, state.getValue(PaintedAsphaltSlope.LAYERS))
                            .setValue(AsphaltSlope.WATERLOGGED, state.getValue(PaintedAsphaltSlope.WATERLOGGED))
                    : type.getBlock().defaultBlockState();
            BakedModel base = models.get(baseState);
            if (base == null) continue;
            boolean fullPaint = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_pattern_0");
            boolean paintedBase = fullPaint || state.getValue(RoadBlock.BASE_PAINTED);
            // Multipart markings share one model across all heights. Keep their base
            // state in the cache key so each slope retains its own geometry and UVs.
            var key = new PreservedAsphaltModel.CacheKey(entry.getValue(), baseState, paintedBase, fullPaint);
            BakedModel wrapper = wrappers.computeIfAbsent(key, ignored -> {
                TextureAtlasSprite sprite = shaper.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .getSprite(new ResourceLocation("trafficengine", "block/" + type.getRoadType() + "_painted"));
                return new PreservedAsphaltModel(entry.getValue(), base, baseState, sprite,
                        paintedBase, fullPaint, paintedQuads.computeIfAbsent(type, unused -> new ConcurrentHashMap<>()));
            });
            result.put(state, wrapper);
        }
        // Rebuilt on resource reload; wrappers and tinted quads share the atlas lifetime.
        return result;
    }

}
