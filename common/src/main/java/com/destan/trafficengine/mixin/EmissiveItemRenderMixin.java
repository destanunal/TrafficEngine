package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.model.EmissiveQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import net.minecraft.client.renderer.LightTexture;

/** Mixin 0.8.5 cannot inject into VertexConsumer's interface default methods. */
@Mixin(ItemRenderer.class)
public abstract class EmissiveItemRenderMixin {
    @Group(name = "trafficengine$itemEmission", min = 1, max = 1)
    @WrapOperation(method = "renderQuadList", require = 0, at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFII)V"))
    private static void trafficengine$emissiveQuad(VertexConsumer consumer, PoseStack.Pose pose,
            BakedQuad quad, float red, float green, float blue, int light, int overlay,
            Operation<Void> original) {
        if (quad instanceof EmissiveQuad) {
            consumer.putBulkData(pose, quad, EmissiveQuad.BRIGHTNESS, red, green, blue,
                EmissiveQuad.LIGHT, overlay, false);
        } else {
            original.call(consumer, pose, quad, red, green, blue, light, overlay);
        }
    }

    // Forge adds alpha/read-color arguments to the same item rendering call.
    @Group(name = "trafficengine$itemEmission", min = 1, max = 1)
    @WrapOperation(method = "renderQuadList", require = 0, at = @At(value = "INVOKE", remap = false,
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIZ)V"))
    private static void trafficengine$forgeEmissiveQuad(VertexConsumer consumer, PoseStack.Pose pose,
            BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay,
            boolean readColor, Operation<Void> original) {
        original.call(consumer, pose, quad, red, green, blue, alpha,
            quad instanceof EmissiveQuad ? LightTexture.FULL_BRIGHT : light, overlay, readColor);
    }

}
