package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.model.EmissiveQuad;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(VertexConsumer.class)
public interface EmissiveVertexConsumerMixin {
    @ModifyVariable(method="putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[IIZ)V",at=@At("HEAD"),argsOnly=true)
    private float[] trafficengine$brightness(float[] value, PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, int[] light, int overlay, boolean colors) {
        return quad instanceof EmissiveQuad ? EmissiveQuad.BRIGHTNESS : value;
    }
    @ModifyVariable(method="putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[IIZ)V",at=@At("HEAD"),argsOnly=true)
    private int[] trafficengine$light(int[] value, PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, int[] light, int overlay, boolean colors) {
        return quad instanceof EmissiveQuad ? EmissiveQuad.LIGHT : value;
    }
}
