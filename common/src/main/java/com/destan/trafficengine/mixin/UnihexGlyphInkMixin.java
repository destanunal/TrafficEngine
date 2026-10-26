package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.gui.MenuGlyphInk;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="net.minecraft.client.gui.font.providers.UnihexProvider$Glyph")
public abstract class UnihexGlyphInkMixin implements MenuGlyphInk {
    @Shadow @Final private UnihexProvider.LineData contents;
    @Override public Ink trafficengine$ink(){return MenuGlyphInk.unihex(contents);}
}
