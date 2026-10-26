package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.destan.trafficengine.client.lighting.RgbLightMath;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium 0.6/0.8 FRAPI path used by both dynamic-light mods. Retain their brightness. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext", remap = false, priority = 900)
public abstract class SodiumColoredLightMixin {
    @Shadow(remap = false) protected BlockPos pos;
    @Inject(method = "shadeQuad", at = @At("TAIL"), require = 0)
    private void trafficengine$rgb(@Coerce Object object, @Coerce Object lightMode, boolean emissive,
            @Coerce Object shadeMode, CallbackInfo callback) {
        if (emissive || !(object instanceof MutableQuadView quad) || quad.lightFace() == null) return;
        if (quad.lightmap(0) == 0xF000F0 && quad.lightmap(1) == 0xF000F0
                && quad.lightmap(2) == 0xF000F0 && quad.lightmap(3) == 0xF000F0) return;
        var tint = ClientColoredLighting.tint(pos, quad.lightFace(), quad.lightmap(0), quad.lightmap(1), quad.lightmap(2), quad.lightmap(3));
        if (tint == null) return;
        int minimum = tint.brightness() << 4;
        for (int i = 0; i < 4; i++) {
            quad.color(i, RgbLightMath.tintArgb(quad.color(i), tint.red(), tint.green(), tint.blue()));
            int light = quad.lightmap(i);
            if ((light & 0xF0) < minimum) quad.lightmap(i, (light & ~0xF0) | minimum);
        }
    }
}
