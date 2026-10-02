package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.Util;
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
        if (active && selected && !primary && width > 10) {
            g.fill(getX() + 4, getY() + height - 2, getX() + width - 4, getY() + height - 1, selectedBorder());
        }
    }
    protected void renderLabel(GuiGraphics g, int color) {
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        int available = Math.max(1, width - 8);
        int y = getY() + (height - font.lineHeight) / 2;
        if (textWidth <= available) {
            g.drawString(font, getMessage(), getX() + (width - textWidth) / 2, y, color, false);
        } else {
            // Match vanilla's smooth scrolling, with a crisp shadow-free caption.
            double seconds = Util.getMillis() / 1000.0;
            double period = Math.max(3, (textWidth - available) / 30.0);
            int offset = (int)Math.round((Math.sin(Math.PI / 2 * Math.cos(2 * Math.PI * seconds / period)) / 2 + 0.5)
                * (textWidth - available));
            // Scissor coordinates are screen coordinates, even when a menu is scaled.
            var transform = g.pose().last().pose();
            g.enableScissor((int)Math.floor((getX() + 4) * transform.m00() + transform.m30()),
                (int)Math.floor(getY() * transform.m11() + transform.m31()),
                (int)Math.ceil((getX() + width - 4) * transform.m00() + transform.m30()),
                (int)Math.ceil((getY() + height) * transform.m11() + transform.m31()));
            g.drawString(font, getMessage(), getX() + 4 - offset, y, color, false);
            g.disableScissor();
        }
    }
}
