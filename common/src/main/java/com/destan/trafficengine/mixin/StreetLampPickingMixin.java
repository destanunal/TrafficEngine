package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.StreetLampTargeting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class StreetLampPickingMixin {
    @Inject(method = "pick(DFZ)Lnet/minecraft/world/phys/HitResult;", at = @At("RETURN"), cancellable = true)
    private void trafficengine$pickExtendedLamp(double reach, float partialTick, boolean fluids,
                                               CallbackInfoReturnable<HitResult> cir) {
        Entity camera = (Entity)(Object)this;
        if (!camera.level().isClientSide) return;
        HitResult vanilla = cir.getReturnValue();
        HitResult result = StreetLampTargeting.pick(camera, reach, partialTick, vanilla);
        if (result != vanilla) cir.setReturnValue(result);
    }
}
