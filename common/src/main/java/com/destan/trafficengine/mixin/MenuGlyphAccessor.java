package com.destan.trafficengine.mixin;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BakedGlyph.class)
public interface MenuGlyphAccessor {
    @Accessor("up") float trafficengine$top();
    @Accessor("down") float trafficengine$bottom();
}
