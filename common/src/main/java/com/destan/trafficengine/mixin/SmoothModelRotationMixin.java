package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.model.SmoothModelAngles;
import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla FaceBakery already handles arbitrary angles; opt in only our marked JSON segments. */
@Mixin(targets="net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class SmoothModelRotationMixin {
    @Inject(method="getAngle",at=@At("HEAD"),cancellable=true)
    private void trafficengine$curveAngle(JsonObject rotation,CallbackInfoReturnable<Float> callback){
        if(SmoothModelAngles.supported(rotation))callback.setReturnValue(SmoothModelAngles.angle(rotation));
    }
}
