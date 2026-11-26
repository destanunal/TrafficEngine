package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.mixin.MenuFontAccessor;
import com.destan.trafficengine.mixin.MenuGlyphAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Glyph bounds are measured only after a caption/resource change, with a 1,024-entry cap. */
public final class MenuTextBounds {
    public record Bounds(float top,float bottom,int width,boolean lineBreak) {}
    private static final Bounds FALLBACK=new Bounds(0,7,0,false);
    private static final IdentityHashMap<Object,Bounds> CACHE=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,MenuGlyphInk.Ink> GLYPHS=new IdentityHashMap<>();
    private static net.minecraft.locale.Language lastLanguage;
    private static Font lastFont;
    private static FontSet lastSet;
    private static volatile int revision;
    private static int cachedRevision;
    private MenuTextBounds(){}
    public static void invalidate(){revision++;}
    public static int revision() { return revision; }
    public static Bounds of(Font font,Object caption) {
        MenuFontAccessor access=font instanceof MenuFontAccessor a?a:null;
        FontSet primary=access==null?null:access.trafficengine$fontSet(Style.DEFAULT_FONT);
        if(font!=lastFont || primary!=lastSet || revision!=cachedRevision || lastLanguage!=net.minecraft.locale.Language.getInstance()) {
            CACHE.clear();GLYPHS.clear();WRAPPED.clear();lastFont=font;lastSet=primary;cachedRevision=revision;lastLanguage=net.minecraft.locale.Language.getInstance();
        }
        Bounds existing=CACHE.get(caption);if(existing!=null)return existing;
        FormattedCharSequence chars=caption instanceof Component c?c.getVisualOrderText()
            :caption instanceof FormattedCharSequence sequence?sequence:FormattedCharSequence.forward((String)caption,Style.EMPTY);
        boolean lineBreak = caption instanceof Component component && component.visit(part ->
            part.indexOf('\n') >= 0 ? Optional.of(Boolean.TRUE) : Optional.empty()).isPresent();
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
        Bounds result=Float.isFinite(vertical[0])?new Bounds(vertical[0],vertical[1],width,lineBreak):new Bounds(FALLBACK.top(),FALLBACK.bottom(),width,lineBreak);
        if(CACHE.size()>=1024)CACHE.clear();CACHE.put(caption,result);return result;
    }
    record WrappedLine(FormattedCharSequence text, int width) {}
    record Wrapped(int width, List<WrappedLine> lines, float top, float bottom, int widest) {}
    private static final IdentityHashMap<FormattedText, List<Wrapped>> WRAPPED = new IdentityHashMap<>();

    static Wrapped wrapped(Font font, FormattedText caption, int width) {
        List<Wrapped> layouts = WRAPPED.get(caption);
        if (layouts != null) {
            for (int i = 0; i < layouts.size(); i++) {
                Wrapped layout = layouts.get(i);
                if (layout.width() == width) return layout;
            }
        } else {
            if (WRAPPED.size() >= 256) WRAPPED.clear();
            layouts = new ArrayList<>(2);
            WRAPPED.put(caption, layouts);
        }

        var split = font.split(caption, width);
        List<WrappedLine> lines = new ArrayList<>(split.size());
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        int widest = 0;
        for (int i = 0; i < split.size(); i++) {
            var line = split.get(i);
            Bounds ink = of(font, line);
            int y = i * font.lineHeight;
            top = Math.min(top, y + ink.top());
            bottom = Math.max(bottom, y + ink.bottom());
            widest = Math.max(widest, ink.width());
            lines.add(new WrappedLine(line, ink.width()));
        }
        if (layouts.size() == 4) layouts.remove(0);
        Wrapped layout = new Wrapped(width, List.copyOf(lines), top, bottom, widest);
        layouts.add(layout);
        return layout;
    }

    private static final float GLYPH_ORIGIN=0;
    public static int centered(int top,int height,float scale,Bounds bounds) {
        return Math.round(centeredExact(top,height,scale,bounds));
    }
    public static float centeredExact(int top,int height,float scale,Bounds bounds) {
        return top+(height-(bounds.bottom()-bounds.top())*scale)/2F-bounds.top()*scale;
    }
}
