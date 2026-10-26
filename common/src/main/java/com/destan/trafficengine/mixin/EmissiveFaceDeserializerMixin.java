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
        if (data != null && data.has("vertices")) {
            var array = data.getAsJsonArray("vertices");
            if (array.size() != 12) throw new com.google.gson.JsonParseException("A model face needs four xyz corners");
            float[] vertices = new float[12];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = array.get(i).getAsFloat();
                if (!Float.isFinite(vertices[i]) || vertices[i] < -32 || vertices[i] > 64)
                    throw new com.google.gson.JsonParseException("Invalid model face corner");
            }
            ((EmissiveFace) (Object) cir.getReturnValue()).trafficengine$setVertices(vertices);
        }
        if (data != null && data.has("match_straight_shade")) {
            ((EmissiveFace) (Object) cir.getReturnValue()).trafficengine$setMatchesStraightShade(
                data.get("match_straight_shade").getAsBoolean());
        }
        if(data!=null && data.has("emissive")) ((EmissiveFace)(Object)cir.getReturnValue()).trafficengine$setEmissive(data.get("emissive").getAsBoolean());
    }
}
