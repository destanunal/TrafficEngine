package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared frame for native screens and the inventory workbench. */
public final class TEChrome {
    private TEChrome() {}
    public static void draw(GuiGraphics g, Font font, int x, int y, int width, int height, Component title) {
        TEPanel.fillRounded(g, x + 3, y + 4, width, height, 0x60000000);
        TEPanel.surface(g, x, y, width, height, TEColors.WINDOW, TEColors.BORDER);
        TEPanel.fillRounded(g, x + 1, y + 1, width - 2, 35, TEColors.HEADER);
        g.fill(x + 1, y + 8, x + width - 1, y + 36, TEColors.HEADER);
        TEPanel.fillRounded(g, x + 8, y + 8, 24, 21, TEColors.ACCENT_SOFT);
        g.drawString(font, "TE", x + 20 - font.width("TE") / 2, y + 14, TEColors.ACCENT, false);
        g.drawString(font, "TRAFFIC ENGINE", x + 40, y + 7, TEColors.MUTED, false);
        g.drawString(font, title, x + 40, y + 19, TEColors.TEXT, false);
        g.fill(x + 8, y + 35, x + width - 8, y + 36, TEColors.BORDER);
        g.fill(x + 8, y + 35, x + 32, y + 36, TEColors.ACCENT);
    }
}
