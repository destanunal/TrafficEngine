package com.destan.trafficengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.model.light.data.QuadLightData", remap=false)
public interface EmbeddiumLightDataAccessor {
    @Accessor(value="lm", remap=false) int[] trafficengine$lightmap();
}
