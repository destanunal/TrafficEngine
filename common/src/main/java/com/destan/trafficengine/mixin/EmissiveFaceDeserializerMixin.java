package com.destan.trafficengine.mixin;
import java.lang.reflect.Type;
import com.google.gson.JsonElement;
import com.google.gson.JsonDeserializationContext;
import com.destan.trafficengine.client.model.EmissiveFace;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.client.renderer.block.model.BlockElementFace$Deserializer")
public class EmissiveFaceDeserializerMixin {
    @Inject(method="deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockElementFace;",at=@At("RETURN"))
    private void trafficengine$read(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<BlockElementFace> cir) {
        var data=json.getAsJsonObject().getAsJsonObject("trafficengine_data");
        if(data!=null && data.has("emissive")) ((EmissiveFace)cir.getReturnValue()).trafficengine$setEmissive(data.get("emissive").getAsBoolean());
    }
}
