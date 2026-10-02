package com.destan.trafficengine.client.gui.components;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Native keyboard/mouse slider; captions are updated only when its value changes. */
public class TEIntegerSlider extends AbstractSliderButton {
    private final int min, max, step;
    private final IntFunction<Component> caption;
    private final IntConsumer change;
    public TEIntegerSlider(int x, int y, int width, int min, int max, int step, int initial,
            IntFunction<Component> caption, IntConsumer change) {
        super(x, y, width, 20, Component.empty(), (double)(initial - min) / Math.max(1, max - min));
        this.min = min; this.max = max; this.step = step;
        this.caption = caption; this.change = change;
        value = Mth.clamp(value, 0, 1);
        updateMessage();
    }
    public int intValue() { return Mth.clamp(min + (int)Math.round(value * (max - min) / step) * step, min, max); }
    public void refreshCaption() { updateMessage(); }
    @Override protected void updateMessage() { if (caption != null) setMessage(caption.apply(intValue())); }
    @Override protected void applyValue() { change.accept(intValue()); }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TEPanel.surface(g, getX(), getY(), width, height, TEColors.FIELD,
            isHoveredOrFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
        int knob = getX() + 4 + (int)Math.round((width - 8) * value);
        g.fill(getX() + 4, getY() + height - 4, getX() + width - 4, getY() + height - 2, TEColors.BORDER);
        g.fill(getX() + 4, getY() + height - 4, knob, getY() + height - 2, TEColors.ACCENT);
        TEPanel.fillRounded(g, knob - 3, getY() + height - 6, 6, 6, TEColors.ACCENT);
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(getMessage().getString(), width - 12);
        g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 3, TEColors.TEXT, false);
    }
}
