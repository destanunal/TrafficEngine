package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Restyles the field while retaining vanilla typing, selection, cursor and hit bounds. */
public class TEEditBox extends EditBox {
    public TEEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setTextColor(TEColors.TEXT);
        setTextColorUneditable(TEColors.MUTED);
    }
    protected int frameColor() { return isFocused() ? TEColors.SELECTION_BORDER : TEColors.SUBTLE_BORDER; }
    @Override public int getInnerWidth() { return Math.max(0, width - 8); }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        TEPanel.surface(g, getX() - 1, getY() - 1, width + 2, height + 2, TEColors.FIELD, frameColor());
        // Use the same four-pixel text inset as a bordered vanilla field.
        setBordered(false);
        g.pose().pushPose();
        g.pose().translate(4, (height - 8) / 2, 0);
        try {
            super.renderWidget(g, mouseX, mouseY, partialTick);
        } finally {
            g.pose().popPose();
            setBordered(true);
        }
    }
}
