package com.destan.trafficengine.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LED items use more layers than Minecraft's default five. */
@Mixin(ItemModelGenerator.class)
public class ItemModelLayersMixin {
    @Shadow @Final @Mutable private static List<String> LAYERS;
    @Inject(method="<clinit>",at=@At("TAIL"))
    private static void trafficengine$extendLayers(CallbackInfo ci) {
        LAYERS=new ArrayList<>(LAYERS);
        for(int i=5;i<=8;i++) if(!LAYERS.contains("layer"+i)) LAYERS.add("layer"+i);
    }
}
