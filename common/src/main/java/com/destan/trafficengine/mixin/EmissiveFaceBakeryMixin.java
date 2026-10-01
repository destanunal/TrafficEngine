package com.destan.trafficengine.mixin;
import org.joml.Vector3f;
import com.destan.trafficengine.client.model.EmissiveFace;
import com.destan.trafficengine.client.model.EmissiveQuad;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FaceBakery.class)
public class EmissiveFaceBakeryMixin {
    @Inject(method="bakeQuad",at=@At("RETURN"),cancellable=true)
    private void trafficengine$bake(Vector3f from, Vector3f to, BlockElementFace face, TextureAtlasSprite sprite, Direction direction, ModelState state, BlockElementRotation rotation, boolean shade, CallbackInfoReturnable<BakedQuad> cir) {
        if(((EmissiveFace)(Object)face).trafficengine$isEmissive()) cir.setReturnValue(new EmissiveQuad(cir.getReturnValue()));
    }
}
