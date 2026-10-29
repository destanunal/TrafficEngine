package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.TrafficSignOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class TrafficSignOutlineMixin {
    @Inject(method="renderHitOutline",at=@At("HEAD"),cancellable=true)
    private void trafficengine$cleanSignOutline(PoseStack pose,VertexConsumer lines,Entity camera,double x,double y,double z,BlockPos pos,BlockState state,CallbackInfo ci) {
        if(TrafficSignOutline.render(pose,lines,state,pos,x,y,z))ci.cancel();
    }
}
