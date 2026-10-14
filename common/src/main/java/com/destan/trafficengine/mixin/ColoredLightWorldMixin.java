package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientLevel.class)
public class ColoredLightWorldMixin {
    @Inject(method="setBlock",at=@At("RETURN"))
    private void trafficengine$occlusionChanged(BlockPos pos,BlockState state,int flags,int limit,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValueZ())ClientColoredLighting.blockChanged(pos);
    }
}
