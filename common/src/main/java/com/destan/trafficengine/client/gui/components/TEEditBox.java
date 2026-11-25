package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Restyles the field while retaining vanilla typing, selection, cursor and hit bounds. */
public class TEEditBox extends EditBox {
    private final Font fieldFont;
    private boolean centered;
    private Component hint;
    public TEEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        fieldFont = font;
        setTextColor(TEColors.TEXT);
        setTextColorUneditable(TEColors.MUTED);
    }
    public TEEditBox centered() {
        centered = true;
        return this;
    }

    @Override public void setHint(Component hint) {
        this.hint = hint;
        super.setHint(hint);
    }

    private int textInset(Object caption) {
        return centered ? Math.max(4, (width - com.destan.trafficengine.client.gui.TEText.width(fieldFont, caption)) / 2) : 4;
    }

    @Override public void onClick(double x, double y) {
        if (!centered) {
            super.onClick(x, y);
            return;
        }
        setBordered(false);
        try {
            super.onClick(x - textInset(getValue()), y);
        } finally {
            setBordered(true);
        }
    }

    protected int frameColor() { return isFocused() ? TEColors.SELECTION_BORDER : TEColors.SUBTLE_BORDER; }
    @Override public int getInnerWidth() { return Math.max(0, width - 8); }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        TEPanel.surface(g, getX() - 1, getY() - 1, width + 2, height + 2, TEColors.FIELD, frameColor());
        // Use the same four-pixel text inset as a bordered vanilla field.
        setBordered(false);
        g.pose().pushPose();
        Object caption = hint != null && !isFocused() && getValue().isEmpty() ? hint : getValue();
        g.pose().translate(textInset(caption), com.destan.trafficengine.client.gui.TEText.centeredY(g, fieldFont, caption, 0, height), 0);
        try {
            super.renderWidget(g, mouseX, mouseY, partialTick);
        } finally {
            g.pose().popPose();
            setBordered(true);
        }
    }
}
