package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public class ColoredLightSourceMixin {
    @Inject(method="setLevel",at=@At("TAIL"))
    private void trafficengine$registerLight(Level level,CallbackInfo ci) { ClientColoredLighting.register((BlockEntity)(Object)this); }
}
