package com.destan.trafficengine.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;

/** Clips at complete Unicode glyphs so moving text never leaves its display. */
public final class ScrollingText {
    public record Window(String text, float x) {}
    private record Line(int width, int[] ends, int[] advances) {}

    private static final int CACHE_LIMIT = 192;
    private static final Window EMPTY = new Window("", 0);
    private static final Map<String, Line> LINES = new LinkedHashMap<>(32, .75F, true);
    private static Font cachedFont;

    private ScrollingText() {}

    public static void invalidate() {
        LINES.clear();
        cachedFont = null;
    }

    public static Window window(String text, Font font, float width, double ticks, int speed) {
        if (text.isEmpty() || width <= 0) return EMPTY;
        if (cachedFont != font) {
            LINES.clear();
            cachedFont = font;
        }
        Line line = LINES.get(text);
        if (line == null) {
            line = measure(text, font::width);
            if (LINES.size() == CACHE_LIMIT) LINES.remove(LINES.keySet().iterator().next());
            LINES.put(text, line);
        }
        return clip(text, line, width, ticks, speed);
    }

    public static Window window(String text, ToIntFunction<String> widthOf, float width, double ticks, int speed) {
        return text.isEmpty() || width <= 0 ? EMPTY : clip(text, measure(text, widthOf), width, ticks, speed);
    }

    private static Line measure(String text, ToIntFunction<String> widthOf) {
        int count = text.codePointCount(0, text.length());
        int[] ends = new int[count];
        int[] advances = new int[count];
        for (int i = 0, start = 0; i < count; i++) {
            int end = start + Character.charCount(text.codePointAt(start));
            ends[i] = end;
            advances[i] = widthOf.applyAsInt(text.substring(start, end));
            start = end;
        }
        return new Line(widthOf.applyAsInt(text), ends, advances);
    }

    private static Window clip(String text, Line line, float width, double ticks, int speed) {
        float cycle = line.width + width + 12;
        float x = width - (float)((ticks * Math.max(1, Math.min(3, speed)) * 0.25) % cycle);
        int start = 0, end = 0;
        float startX = x;
        for (int i = 0; i < line.ends.length; i++) {
            int next = line.ends[i];
            float advance = line.advances[i];
            if (x < 0) {
                start = next;
                startX = x + advance;
            } else if (x + advance > width) {
                break;
            }
            x += advance;
            end = next;
        }
        return new Window(text.substring(Math.min(start, end), end), startX);
    }
}
