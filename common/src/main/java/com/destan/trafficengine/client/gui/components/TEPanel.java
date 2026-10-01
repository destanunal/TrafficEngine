package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;

public final class TEPanel {
    private TEPanel() {}
    public static void draw(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, TEColors.PANEL);
        outline(g, x, y, width, height, TEColors.BORDER);
    }
    public static void outline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
    }
}
