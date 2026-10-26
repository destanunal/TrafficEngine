package com.destan.trafficengine.mixin;
import com.destan.trafficengine.client.gui.MenuGlyphInk;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")
public abstract class BitmapGlyphInkMixin implements MenuGlyphInk {
    @Shadow @Final private NativeImage image;
    @Shadow @Final private int offsetX;
    @Shadow @Final private int offsetY;
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final private float scale;
    @Shadow @Final private int ascent;
    @Override public Ink trafficengine$ink(){return MenuGlyphInk.bitmap(image,offsetX,offsetY,width,height,scale,ascent);}
}
