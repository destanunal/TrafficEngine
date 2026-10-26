package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.model.EmissiveQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mixin 0.8.5 cannot inject into VertexConsumer's interface default methods. */
@Mixin(ModelBlockRenderer.class)
public abstract class EmissiveQuadRenderMixin {
    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE", target =
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
}
