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
        g.fill(x + 3, y + 3, x + width + 3, y + height + 3, 0x70000000);
        g.fill(x, y, x + width, y + height, TEColors.WINDOW);
        TEPanel.outline(g, x, y, width, height, TEColors.BORDER);
        g.fill(x + 8, y + 8, x + 32, y + 27, TEColors.ACCENT);
        g.drawString(font, "TE", x + 20 - font.width("TE") / 2, y + 13, TEColors.INK, false);
        g.drawString(font, "TRAFFIC ENGINE", x + 40, y + 7, TEColors.MUTED, false);
        g.drawString(font, title, x + 40, y + 19, TEColors.TEXT, false);
        g.fill(x + 8, y + 33, x + width - 8, y + 35, TEColors.ACCENT);
    }
}
