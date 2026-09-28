package com.destan.trafficengine.mixin;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A multipart model is state-aware: getQuads receives the block state and
 * tests the selectors then. Baking an identical selector list for every road
 * pattern state only duplicates large lists of predicates and model pairs.
 * Keep one baked model per pattern block, without changing any saved states.
 */
@Mixin(MultiPart.class)
public abstract class MultipartPatternModelCacheMixin {
    @Unique
    private final AtomicReference<BakedModel> trafficengine$sharedPatternModel = new AtomicReference<>();

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void trafficengine$reusePatternModel(ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            ResourceLocation modelLocation, CallbackInfoReturnable<BakedModel> callback) {
        if (trafficengine$isPatternBlock(modelLocation, modelState)) {
            BakedModel cached = trafficengine$sharedPatternModel.get();
            if (cached != null) callback.setReturnValue(cached);
        }
    }

    @Inject(method = "bake", at = @At("RETURN"), cancellable = true)
    private void trafficengine$rememberPatternModel(ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            ResourceLocation modelLocation, CallbackInfoReturnable<BakedModel> callback) {
        if (!trafficengine$isPatternBlock(modelLocation, modelState)) return;
        BakedModel baked = callback.getReturnValue();
        if (baked == null) return;
        if (!trafficengine$sharedPatternModel.compareAndSet(null, baked)) {
            callback.setReturnValue(trafficengine$sharedPatternModel.get());
        }
    }

    @Unique
    private static boolean trafficengine$isPatternBlock(ResourceLocation modelLocation, ModelState modelState) {
        return modelState == BlockModelRotation.X0_Y0
            && modelLocation instanceof ModelResourceLocation stateModel
            && "trafficengine".equals(stateModel.getNamespace())
            && stateModel.getPath().contains("_pattern_");
    }
}
