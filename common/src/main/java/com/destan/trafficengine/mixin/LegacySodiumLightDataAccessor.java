package com.destan.trafficengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData", remap=false)
public interface LegacySodiumLightDataAccessor {
    @Accessor(value="lm", remap=false) int[] trafficengine$lightmap();
}
