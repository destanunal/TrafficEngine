package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.mixin.MenuFontAccessor;
import com.destan.trafficengine.mixin.MenuGlyphAccessor;
import java.util.IdentityHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Glyph bounds are measured only after a caption/resource change, with a 1,024-entry cap. */
public final class MenuTextBounds {
    public record Bounds(float top,float bottom,int width) {}
    private static final Bounds FALLBACK=new Bounds(0,7,0);
    private static final IdentityHashMap<Object,Bounds> CACHE=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,MenuGlyphInk.Ink> GLYPHS=new IdentityHashMap<>();
    private static net.minecraft.locale.Language lastLanguage;
    private static Font lastFont;
    private static FontSet lastSet;
    private static volatile int revision;
    private static int cachedRevision;
    private MenuTextBounds(){}
    public static void invalidate(){revision++;}
    public static Bounds of(Font font,Object caption) {
        MenuFontAccessor access=font instanceof MenuFontAccessor a?a:null;
        FontSet primary=access==null?null:access.trafficengine$fontSet(Style.DEFAULT_FONT);
        if(font!=lastFont || primary!=lastSet || revision!=cachedRevision || lastLanguage!=net.minecraft.locale.Language.getInstance()) {
            CACHE.clear();GLYPHS.clear();lastFont=font;lastSet=primary;cachedRevision=revision;lastLanguage=net.minecraft.locale.Language.getInstance();
        }
        Bounds existing=CACHE.get(caption);if(existing!=null)return existing;
        FormattedCharSequence chars=caption instanceof Component c?c.getVisualOrderText()
            :caption instanceof FormattedCharSequence sequence?sequence:FormattedCharSequence.forward((String)caption,Style.EMPTY);
        float[] vertical={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
        chars.accept((index,style,codepoint)->{
            if(access!=null && !Character.isWhitespace(codepoint)) {
                FontSet set=access.trafficengine$fontSet(style.getFont());
                var info=set.getGlyphInfo(codepoint,false);
                MenuGlyphInk.Ink ink=GLYPHS.get(info);
                if(ink==null) {
                    if(info instanceof MenuGlyphInk source)ink=source.trafficengine$ink();
                    else {
                        var glyph=set.getGlyph(codepoint);
                        if(glyph instanceof MenuGlyphAccessor bounds)ink=new MenuGlyphInk.Ink(bounds.trafficengine$top()-GLYPH_ORIGIN,bounds.trafficengine$bottom()-GLYPH_ORIGIN);
                        else ink=new MenuGlyphInk.Ink(0,7);
                    }
                    if(GLYPHS.size()>=4096)GLYPHS.clear();GLYPHS.put(info,ink);
                }
                if(ink.bottom()>ink.top()) {
                    vertical[0]=Math.min(vertical[0],ink.top());vertical[1]=Math.max(vertical[1],ink.bottom());
                }
            }
            return true;
        });
        int width=font.width(chars);
        Bounds result=Float.isFinite(vertical[0])?new Bounds(vertical[0],vertical[1],width):new Bounds(FALLBACK.top(),FALLBACK.bottom(),width);
        if(CACHE.size()>=1024)CACHE.clear();CACHE.put(caption,result);return result;
    }
    private static final float GLYPH_ORIGIN=3;
    public static int centered(int top,int height,float scale,Bounds bounds) {
        return Math.round(centeredExact(top,height,scale,bounds));
    }
    public static float centeredExact(int top,int height,float scale,Bounds bounds) {
        return top+(height-(bounds.bottom()-bounds.top())*scale)/2F-bounds.top()*scale;
    }
}
