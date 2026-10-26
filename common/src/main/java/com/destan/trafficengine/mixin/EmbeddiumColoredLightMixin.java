package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.destan.trafficengine.client.lighting.RgbLightMath;
import com.destan.trafficengine.client.model.EmissiveQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Reuse Embeddium's quad buffers; no reflective lookup, extra geometry or per-frame scan. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap=false)
public abstract class EmbeddiumColoredLightMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<int[]> trafficengine$plainColors = ThreadLocal.withInitial(() -> new int[4]);

    @ModifyVariable(method="writeGeometry", at=@At("HEAD"), argsOnly=true, ordinal=0, require=0)
    private int[] trafficengine$coloredSurface(int[] input, @Coerce Object context, @Coerce Object builder,
            Vec3 offset, @Coerce Object material, @Coerce Object quad, int[] originalColors,
            @Coerce Object lighting) {
        if (!(quad instanceof BakedQuad baked) || quad instanceof EmissiveQuad) return input;
        int[] light = ((EmbeddiumLightDataAccessor)lighting).trafficengine$lightmap();
        var tint = ClientColoredLighting.tint(((EmbeddiumBlockContextAccessor)context).trafficengine$position(),
                baked.getDirection(), light);
        if (tint == null) return input;
        int[] colors = input;
        if (colors == null) {
            colors = trafficengine$plainColors.get();
            java.util.Arrays.fill(colors, 0xFFFFFFFF);
        }
        for (int i=0;i<4;i++) {
            colors[i] = RgbLightMath.tintAbgr(colors[i], tint.red(), tint.green(), tint.blue());
            int minimum = tint.brightness()<<4;
            if ((light[i]&0xF0)<minimum) light[i]=(light[i]&~0xF0)|minimum;
        }
        return colors;
    }
}
