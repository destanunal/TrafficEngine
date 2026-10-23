package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.client.gui.TEText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Shared, labelled drawing tools; icons use the existing native workbench strokes. */
public final class TEPaintToolButton extends TEButton {
    private final ModGuiIcons icon;

    public TEPaintToolButton(int x, int y, int width, boolean eraser, OnPress action) {
        super(x, y, width, 22, Component.translatable("gui.trafficengine.drawing." + (eraser ? "eraser" : "pencil")), action);
        icon = eraser ? ModGuiIcons.ERASE : ModGuiIcons.EDIT;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override protected void renderLabel(GuiGraphics g, int color) {
        var font = Minecraft.getInstance().font;
        float scale = TEText.layoutScale(g);
        int textWidth = Math.round(font.width(getMessage()) * scale);
        if (width < textWidth + 34) {
            TEWorkbenchIcons.draw(g, icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2, color);
            return;
        }
        int x = getX() + (width - 22 - textWidth) / 2;
        TEWorkbenchIcons.draw(g, icon, x, getY() + (height - 16) / 2, color);
        TEText.drawString(g, font, getMessage(), x + 22,
            getY() + (height - Math.round(font.lineHeight * scale)) / 2, color, false);
    }
}
