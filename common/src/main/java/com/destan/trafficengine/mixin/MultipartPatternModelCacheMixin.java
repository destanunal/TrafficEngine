package com.destan.trafficengine.mixin;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A multipart model is state-aware: getQuads receives the block state and
 * tests the selectors then. Baking an identical selector list for every road
 * pattern state only duplicates large lists of predicates and model pairs.
 * Keep one baked model per multipart definition at the default rotation,
 * without changing any saved states.
 */
@Mixin(MultiPart.class)
public abstract class MultipartPatternModelCacheMixin {
    @Shadow @Final
    private StateDefinition<Block, BlockState> definition;

    @Unique
    private final AtomicReference<BakedModel> trafficengine$sharedPatternModel = new AtomicReference<>();

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void trafficengine$reusePatternModel(ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            CallbackInfoReturnable<BakedModel> callback) {
        if (trafficengine$isPatternBlock(modelState)) {
            BakedModel cached = trafficengine$sharedPatternModel.get();
            if (cached != null) callback.setReturnValue(cached);
        }
    }

    @Inject(method = "bake", at = @At("RETURN"), cancellable = true)
    private void trafficengine$rememberPatternModel(ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            CallbackInfoReturnable<BakedModel> callback) {
        if (!trafficengine$isPatternBlock(modelState)) return;
        BakedModel baked = callback.getReturnValue();
        if (baked == null) return;
        if (!trafficengine$sharedPatternModel.compareAndSet(null, baked)) {
            callback.setReturnValue(trafficengine$sharedPatternModel.get());
        }
    }

    @Unique
    private boolean trafficengine$isPatternBlock(ModelState modelState) {
        if (modelState != BlockModelRotation.X0_Y0) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(definition.getOwner());
        return "trafficengine".equals(id.getNamespace()) && id.getPath().contains("_pattern_");
    }
}
