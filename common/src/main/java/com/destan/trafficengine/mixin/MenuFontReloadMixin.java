package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.gui.MenuTextBounds;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FontSet.class)
public abstract class MenuFontReloadMixin {
    @Inject(method="reload",at=@At("HEAD"))
    private void trafficengine$fontReload(CallbackInfo callback){MenuTextBounds.invalidate();com.destan.trafficengine.client.gui.components.TESignalIcons.invalidate();}
}
