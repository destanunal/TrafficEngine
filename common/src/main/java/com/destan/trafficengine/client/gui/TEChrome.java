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
        TEPanel.fillRounded(g, x + 2, y + 3, width, height, 0x48000000);
        TEPanel.surface(g, x, y, width, height, TEColors.WINDOW, TEColors.BORDER);
        TEPanel.fillRounded(g, x + 1, y + 1, width - 2, 35, TEColors.HEADER);
        g.fill(x + 1, y + 8, x + width - 1, y + 36, TEColors.HEADER);
        TEPanel.fillRounded(g, x + 8, y + 8, 24, 21, TEColors.ACCENT_SOFT);
        TEText.drawCenteredString(g,font,"TE",x+20,TEText.centeredY(g,font,"TE",y+8,21),TEColors.ACCENT);
        TEText.drawString(g, font, "TRAFFIC ENGINE", x + 40, TEText.centeredY(g,font,"TRAFFIC ENGINE",y+4,12), TEColors.ACCENT, false);
        TEText.drawString(g, font, title, x + 40, TEText.centeredY(g,font,title,y+17,14), TEColors.TEXT, false);
        g.fill(x + 8, y + 35, x + width - 8, y + 36, TEColors.SUBTLE_BORDER);
    }
}
