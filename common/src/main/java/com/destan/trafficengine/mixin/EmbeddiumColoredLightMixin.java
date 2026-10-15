package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.destan.trafficengine.client.model.EmissiveQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse Embeddium's quad buffers; no reflective lookup, extra geometry or per-frame scan. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap=false)
public abstract class EmbeddiumColoredLightMixin {
    @Inject(method="writeGeometry", at=@At("HEAD"), require=0)
    private void trafficengine$coloredSurface(@Coerce Object context, @Coerce Object builder,
            Vec3 offset, @Coerce Object material, @Coerce Object quad, int[] colors,
            @Coerce Object lighting, CallbackInfo ci) {
        if (!(quad instanceof BakedQuad baked) || quad instanceof EmissiveQuad) return;
        int[] light = ((EmbeddiumLightDataAccessor)lighting).trafficengine$lightmap();
        var tint = ClientColoredLighting.tint(((EmbeddiumBlockContextAccessor)context).trafficengine$position(),
                baked.getDirection(), light);
        if (tint == null) return;
        for (int i=0;i<4;i++) {
            int color = colors[i];
            int red = Math.round((color>>16&255)*tint.red());
            int green = Math.round((color>>8&255)*tint.green());
            int blue = Math.round((color&255)*tint.blue());
            colors[i] = (color&0xFF000000)|(red<<16)|(green<<8)|blue;
            int minimum = tint.brightness()<<4;
            if ((light[i]&0xF0)<minimum) light[i]=(light[i]&~0xF0)|minimum;
        }
    }
}
