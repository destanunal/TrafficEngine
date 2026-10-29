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
@Mixin(targets="net.minecraftforge.client.model.lighting.QuadLighter", remap=false)
public abstract class PlatformColoredLightMixin {
    @Shadow(remap=false) private BlockPos pos;

    @Shadow(remap=false) private net.minecraft.world.level.BlockAndTintGetter level;
    // Capture original method arguments directly. No array-local reconstruction:
    // Forge 47.2.1's Mixin verifier cannot analyze the primitive normal-array merge.
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="process",remap=false,
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/BlockAndTintGetter;getShade(FFFZ)F",remap=false))
    private float trafficengine$placementShade(float shade,VertexConsumer consumer,PoseStack.Pose pose,BakedQuad quad,int overlay) {
        return quad instanceof com.destan.trafficengine.client.model.PlacementShadeQuad matched?matched.shade(level):shade;
    }

    @WrapOperation(method="process", remap=false, at=@At(value="INVOKE", remap=true,
        target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[IIZ)V"))
    private void trafficengine$coloredSurface(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] brightness, float red, float green, float blue, int[] light, int overlay,
            boolean useColor, Operation<Void> original) {
        if (quad instanceof EmissiveQuad) {
            brightness = EmissiveQuad.BRIGHTNESS;
            light = EmissiveQuad.LIGHT;
        }
        var tint = quad instanceof EmissiveQuad ? null : ClientColoredLighting.tint(pos, quad.getDirection(), light);
        if (tint != null) {
            red *= tint.red(); green *= tint.green(); blue *= tint.blue();
            light = ClientColoredLighting.promoteLight(light, tint.brightness());
        }
        original.call(consumer, pose, quad, brightness, red, green, blue, light, overlay, useColor);
    }
}
