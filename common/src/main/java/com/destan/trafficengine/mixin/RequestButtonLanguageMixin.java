package com.destan.trafficengine.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonDeserializationContext;
import java.lang.reflect.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Language switches reload models: choose a static atlas sprite once, never draw world text. */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockModel$Deserializer")
public abstract class RequestButtonLanguageMixin {
    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;", at = @At("HEAD"))
    private void trafficengine$localizedPanel(JsonElement json, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<BlockModel> callback) {
        if (!json.isJsonObject()) return;
        var textures = json.getAsJsonObject().getAsJsonObject("textures");
        if (textures == null || !textures.has("panel") || !textures.get("panel").isJsonPrimitive()
                || !"trafficengine:block/request_button_panel".equals(textures.get("panel").getAsString())) return;
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        String suffix = language.startsWith("tr_") ? "tr" : language.startsWith("de_") ? "de" : "en";
        textures.addProperty("panel", "trafficengine:block/request_button_panel_" + suffix);
    }
}
