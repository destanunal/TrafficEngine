package com.destan.trafficengine.mixin;

import de.mrjulsen.mcdragonlib.DragonLib;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DragonLib.class, remap = false)
public class DragonLibWelcomeMixin {

    // DragonLib welcome method only writes an informational startup banner.
    @Inject(method = "printDraconicWelcomeMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void trafficengine$skipWelcomeBanner(CallbackInfo ci) {
        ci.cancel();
    }
}
