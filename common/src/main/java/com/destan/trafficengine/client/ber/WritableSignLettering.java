package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.client.gui.MenuTextBounds;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Shared lettering for placed address signs and their editor preview. */
public final class WritableSignLettering {
    private static final int MAX_LINES = 1024;
    private static final Style STYLE = Style.EMPTY.withFont(new ResourceLocation("trafficengine", "road_sign_bold"));
    private static final Object2ObjectLinkedOpenHashMap<String, Line> LINES = new Object2ObjectLinkedOpenHashMap<>();
    private static Language language;
    private static Font font;
    private static int fontRevision = -1;

    public record Line(FormattedCharSequence glyphs, int width) {}

    private WritableSignLettering() {}

    public static Line layout(Font activeFont, String text) {
        Language activeLanguage = Language.getInstance();
        int activeRevision = MenuTextBounds.revision();
        if (activeLanguage != language || activeFont != font || activeRevision != fontRevision) {
            LINES.clear();
            language = activeLanguage;
            font = activeFont;
            fontRevision = activeRevision;
        }
        Line existing = LINES.getAndMoveToLast(text);
        if (existing != null) return existing;
        FormattedCharSequence glyphs = Component.literal(text).setStyle(STYLE).getVisualOrderText();
        Line line = new Line(glyphs, activeFont.width(glyphs));
        if (LINES.size() == MAX_LINES) LINES.removeFirst();
        LINES.put(text, line);
        return line;
    }
}
