package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TESearchBox extends EditBox {
    public TESearchBox(Font font, int x, int y, int width, int height, Component hint) {
        super(font, x, y, width, height, hint);
        setHint(hint);
        setMaxLength(80);
        setTextColor(TEColors.TEXT);
        setTextColorUneditable(TEColors.MUTED);
        setBordered(false);
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(getX() - 5, getY() - 5, getX() + width + 5, getY() + height + 5, TEColors.FIELD);
        TEPanel.outline(g, getX() - 5, getY() - 5, width + 10, height + 10, isFocused() ? TEColors.ACCENT : TEColors.BORDER);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
