package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.model.PlacementShadeQuad;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelBlockRenderer.class)
public class PlacementShadingMixin {
    // Correct the final brightness in the shared output path. This also handles
    // smooth lighting and Forge's per-face AO fallback, not only flat lighting.
    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float trafficengine$shade0(float brightness, @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BakedQuad quad) {
        return trafficengine$matchShade(brightness, level, quad);
    }

    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float trafficengine$shade1(float brightness, @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BakedQuad quad) {
        return trafficengine$matchShade(brightness, level, quad);
    }

    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float trafficengine$shade2(float brightness, @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BakedQuad quad) {
        return trafficengine$matchShade(brightness, level, quad);
    }

    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float trafficengine$shade3(float brightness, @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BakedQuad quad) {
        return trafficengine$matchShade(brightness, level, quad);
    }

    @Unique
    private static float trafficengine$matchShade(float brightness, BlockAndTintGetter level, BakedQuad quad) {
        if (!(quad instanceof PlacementShadeQuad matched)) return brightness;
        float actual = level.getShade(quad.getDirection(), quad.isShade());
        if (actual <= 0) return brightness;
        return brightness * level.getShade(matched.getShadeDirection(), quad.isShade()) / actual;
    }
}
