package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.block.data.PaintableRailing;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.packets.cts.RailingPaintErasePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private BlockPos trafficengine$brushRailing() {
        Minecraft mc=(Minecraft)(Object)this;
        if (mc.player==null || mc.level==null || !(mc.player.getMainHandItem().getItem() instanceof BrushItem)
                || !(mc.hitResult instanceof BlockHitResult hit)
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof PaintableRailing)) return null;
        return hit.getBlockPos();
    }
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void trafficengine$eraseRailing(CallbackInfoReturnable<Boolean> ci) {
        var pos=trafficengine$brushRailing();
        if (pos==null) return;
        ((Minecraft)(Object)this).player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        ModNetworkManager.ERASE_RAILING_PAINT.send(
            NetworkDirection.C2S,new RailingPaintErasePacket(pos));
        ci.setReturnValue(true);
    }
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void trafficengine$skipRailingMining(boolean held,CallbackInfo ci) {
        if (held && trafficengine$brushRailing()!=null) ci.cancel();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    public void tc$runAfterClientTick(boolean render, CallbackInfo ci) {
        ClientWrapper.runAllScheduledRenderTasks();
    }
}
