package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ModelBlockRenderer.class)
public class ColoredLightModelMixin {
    @WrapOperation(method="putQuadData",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFF[IIZ)V"))
    private void trafficengine$coloredSurface(VertexConsumer consumer,PoseStack.Pose pose,BakedQuad quad,float[] ao,float r,float g,float b,int[] light,int overlay,boolean useColor,Operation<Void> original,@Local(argsOnly=true) BlockPos pos) {
        if (quad instanceof com.destan.trafficengine.client.model.EmissiveQuad) {
            ao = com.destan.trafficengine.client.model.EmissiveQuad.BRIGHTNESS;
            light = com.destan.trafficengine.client.model.EmissiveQuad.LIGHT;
        }
        var tint=quad instanceof com.destan.trafficengine.client.model.EmissiveQuad ? null : ClientColoredLighting.tint(pos,quad.getDirection(),light);
        if(tint!=null) {
            r*=tint.red();g*=tint.green();b*=tint.blue();light=ClientColoredLighting.promoteLight(light,tint.brightness());
        }
        original.call(consumer,pose,quad,ao,r,g,b,light,overlay,useColor);
    }
}
