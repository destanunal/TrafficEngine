package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class TEButton extends Button {
    private boolean selected;
    private final boolean primary;
    public TEButton(int x, int y, int width, int height, Component text, OnPress action) {
        this(x, y, width, height, text, action, false);
    }
    public TEButton(int x, int y, int width, int height, Component text, OnPress action, boolean primary) {
        super(x, y, width, height, text, action, DEFAULT_NARRATION);
        this.primary = primary;
    }
    public void setSelected(boolean selected) { this.selected = selected; }
    public TEButton primary() { setSelected(true); return this; }
    public TEButton selected(boolean selected) { setSelected(selected); return this; }
    protected boolean isSelected() { return selected; }
    protected int selectedBackground() { return TEColors.ACCENT_SOFT; }
    protected int selectedBorder() { return TEColors.ACCENT; }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHoveredOrFocused();
        boolean highlight = active && (primary || selected);
        int background = !active ? TEColors.WINDOW : primary ? TEColors.ACCENT
            : selected ? selectedBackground() : hover ? TEColors.HOVER : TEColors.CONTROL;
        int border = highlight ? selectedBorder() : hover ? TEColors.SELECTION_BORDER : TEColors.SUBTLE_BORDER;
        TEPanel.surface(g, getX(), getY(), width, height, background, border);
        renderLabel(g, !active ? TEColors.MUTED : primary ? TEColors.INK : selected ? TEColors.ACCENT : TEColors.TEXT);
    }
    protected void renderLabel(GuiGraphics g, int color) {
        TEText.drawWrappedCentered(g, Minecraft.getInstance().font, getMessage(),
            getX() + 4, getY() + 1, width - 8, height - 2, color);
    }
}
