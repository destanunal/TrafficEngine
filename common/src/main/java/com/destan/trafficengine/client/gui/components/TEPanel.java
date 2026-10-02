package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;

public final class TEPanel {
    private TEPanel() {}
    public static void draw(GuiGraphics g, int x, int y, int width, int height) {
        surface(g, x, y, width, height, TEColors.PANEL, TEColors.SUBTLE_BORDER);
    }
    /** Crisp two-pixel corners, with no textures, blur pass or per-frame allocations. */
    public static void fillRounded(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 4 || height <= 4) {
            g.fill(x, y, x + width, y + height, color);
            return;
        }
        g.fill(x + 2, y, x + width - 2, y + height, color);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        g.fill(x, y + 2, x + width, y + height - 2, color);
    }
    public static void surface(GuiGraphics g, int x, int y, int width, int height, int fill, int border) {
        fillRounded(g, x, y, width, height, border);
        fillRounded(g, x + 1, y + 1, width - 2, height - 2, fill);
    }
    public static void outline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
    }
}
