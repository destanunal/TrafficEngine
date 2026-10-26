package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.TEText;
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
    public void setIntValue(int integer) {
        double next = (double)(Mth.clamp(integer, min, max) - min) / Math.max(1, max - min);
        if (next == value) return;
        value = next; applyValue(); updateMessage();
    }
    public void refreshCaption() { updateMessage(); }
    @Override protected void updateMessage() { if (caption != null) setMessage(caption.apply(intValue())); }
    @Override protected void applyValue() { change.accept(intValue()); }
    private Component fittedSource;
    private com.destan.trafficengine.client.gui.MenuTextBounds.Bounds fittedBounds;
    private int fittedWidth;
    private float fittedScale;
    private String fittedText="";
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TEPanel.surface(g, getX(), getY(), width, height, TEColors.FIELD,
            isHoveredOrFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
        int knob = getX() + 4 + (int)Math.round((width - 8) * value);
        g.fill(getX() + 4, getY() + height - 4, getX() + width - 4, getY() + height - 2, TEColors.BORDER);
        g.fill(getX() + 4, getY() + height - 4, knob, getY() + height - 2, TEColors.ACCENT);
        TEPanel.fillRounded(g, knob - 3, getY() + height - 6, 6, 6, TEColors.ACCENT);
        var font = Minecraft.getInstance().font;
        var bounds=com.destan.trafficengine.client.gui.MenuTextBounds.of(font,getMessage());
        float currentScale=TEText.layoutScale(g);
        if(fittedSource!=getMessage() || fittedBounds!=bounds || fittedWidth!=width || fittedScale!=currentScale) {
            fittedSource=getMessage();fittedBounds=bounds;fittedWidth=width;fittedScale=currentScale;
            fittedText=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,(int)((width-12)/TEText.layoutScale(g))));
        }
        TEText.drawCenteredString(g,font,fittedText,getX()+width/2,TEText.centeredY(g,font,fittedText,getY(),height-3),TEColors.TEXT);
    }
}
