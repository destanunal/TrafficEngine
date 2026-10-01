package com.destan.trafficengine.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import com.destan.trafficengine.client.model.EmissiveFace;
@Mixin(BlockElementFace.class)
public class EmissiveFaceMixin implements EmissiveFace {
    @Unique private boolean trafficengine$emissive;
    public boolean trafficengine$isEmissive() {return trafficengine$emissive;}
    public void trafficengine$setEmissive(boolean value) {trafficengine$emissive=value;}
}
