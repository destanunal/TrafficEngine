package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.StreetLampTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiPlayerGameMode.class)
public abstract class StreetLampInteractionMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockHitResult trafficengine$normalizeLampClick(BlockHitResult hit) {
        return minecraft.level == null ? hit : StreetLampTargeting.interactionHit(minecraft.level, hit);
    }
}
