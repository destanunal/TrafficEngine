package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.destan.trafficengine.client.model.EmissiveQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Optional platform pipeline; Fabric keeps using the vanilla renderer hook. */
@Pseudo
@Mixin(targets="net.neoforged.neoforge.client.model.lighting.QuadLighter", remap=false)
public abstract class PlatformColoredLightMixin {
    @Shadow(remap=false) private BlockPos pos;

    @Shadow(remap=false) private net.minecraft.world.level.BlockAndTintGetter level;
    @org.spongepowered.asm.mixin.Unique private com.destan.trafficengine.client.model.PlacementShadeQuad trafficengine$placementQuad;
    @org.spongepowered.asm.mixin.injection.Inject(method="computeLightingForQuad(Lnet/minecraft/client/renderer/block/model/BakedQuad;)V",at=@At("HEAD"),remap=false)
    private void trafficengine$beginPlacementLighting(BakedQuad quad,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        trafficengine$placementQuad=quad instanceof com.destan.trafficengine.client.model.PlacementShadeQuad matched?matched:null;
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="computeLightingForQuad(Lnet/minecraft/client/renderer/block/model/BakedQuad;)V",at=@At("RETURN"),remap=false)
    private void trafficengine$endPlacementLighting(BakedQuad quad,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {trafficengine$placementQuad=null;}
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="computeLightingForQuad([IZ)V",remap=false,
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/BlockAndTintGetter;getShade(FFFZ)F",remap=false))
    private float trafficengine$placementShade(float shade) {return trafficengine$placementQuad==null?shade:trafficengine$placementQuad.shade(level);}

    @WrapOperation(method="process", remap=false, at=@At(value="INVOKE", remap=true,
        target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void trafficengine$coloredSurface(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha, int[] light, int overlay,
            boolean useColor, Operation<Void> original) {
        var tint = quad instanceof EmissiveQuad ? null : ClientColoredLighting.tint(pos, quad.getDirection(), light);
        if (tint != null) {
            red *= tint.red(); green *= tint.green(); blue *= tint.blue();
            light = ClientColoredLighting.promoteLight(light, tint.brightness());
        }
        original.call(consumer, pose, quad, brightness, red, green, blue, alpha, light, overlay, useColor);
    }
}
