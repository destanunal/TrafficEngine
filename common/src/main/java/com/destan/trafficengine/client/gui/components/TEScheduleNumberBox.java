package com.destan.trafficengine.client.gui.components;

import java.util.function.DoubleConsumer;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native schedule field with the original increment/decrement controls. */
public class TEScheduleNumberBox extends EditBox {
    private final int frameWidth;
    private final double min, max;
    private final boolean integer, stepper;

    public TEScheduleNumberBox(Font font, int x, int y, int width, double value,
            double min, double max, boolean integer, boolean stepper, Component label, DoubleConsumer change) {
        super(font, x + 5, y + 5, width - (stepper ? 23 : 10), 9, label);
        frameWidth = width;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.stepper = stepper;
        setBordered(false);
        setTextColor(TEColors.TEXT);
        setMaxLength(integer ? 3 : 6);
        setFilter(text -> text.matches(integer ? "[0-9]{0,3}" : "[0-9]{0,3}(\\.[0-9]{0,2})?"));
        setValue(format(value));
        setResponder(text -> { if (isValid()) change.accept(Double.parseDouble(text)); });
    }

    private static String format(double value) {
        return value == (int)value ? Integer.toString((int)value) : Double.toString(value);
    }
    public boolean isInteger() { return integer; }
    public boolean isValid() {
        try {
            double value = Double.parseDouble(getValue());
            return Double.isFinite(value) && value >= min && value <= max && (!integer || value == Math.floor(value));
        } catch (NumberFormatException ex) { return false; }
    }
    private void step(int direction) {
        double value = isValid() ? Double.parseDouble(getValue()) : min;
        setValue(format(Math.round(Math.max(min, Math.min(max, value + direction)) * 100D) / 100D));
        setCursorPosition(getValue().length());
    }
    @Override public boolean isMouseOver(double x, double y) {
        return visible && x >= getX() - 5 && x < getX() - 5 + frameWidth && y >= getY() - 5 && y < getY() + 13;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!active || button != 0 || !isMouseOver(x, y)) return false;
        setFocused(true);
        if (stepper && x >= getX() - 5 + frameWidth - 16) step(y < getY() + 4 ? 1 : -1);
        else onClick(Math.max(getX(), Math.min(getX() + width, x)), y);
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (isFocused() && stepper && (key == 265 || key == 264)) {
            step(key == 265 ? 1 : -1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private static void fillStepperHalf(GuiGraphics g, int x, int y, boolean upper, int color) {
        // Keep the stepper background inside the frame's rounded right corners.
        if (upper) {
            g.fill(x, y + 1, x + 13, y + 2, color);
            g.fill(x, y + 2, x + 14, y + 3, color);
            g.fill(x, y + 3, x + 15, y + 9, color);
        } else {
            g.fill(x, y + 9, x + 15, y + 15, color);
            g.fill(x, y + 15, x + 14, y + 16, color);
            g.fill(x, y + 16, x + 13, y + 17, color);
        }
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
        int x = getX() - 5, y = getY() - 5;
        TEPanel.surface(g, x, y, frameWidth, 18, TEColors.FIELD,
            !isValid() ? 0xFFE86A63 : isFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
        if (stepper) {
            int bx = x + frameWidth - 16;
            fillStepperHalf(g, bx, y, true, TEColors.PANEL);
            fillStepperHalf(g, bx, y, false, TEColors.PANEL);
            if (active && mouseX >= bx && mouseX < x + frameWidth - 1 && mouseY >= y && mouseY < y + 18) {
                fillStepperHalf(g, bx, y, mouseY < y + 9, TEColors.HOVER);
            }
            g.fill(bx, y + 1, bx + 1, y + 17, TEColors.BORDER);
            g.fill(bx, y + 8, bx + 15, y + 9, TEColors.BORDER);
            int ink = active ? TEColors.TEXT : TEColors.MUTED;
            g.fill(bx + 5, y + 4, bx + 10, y + 5, ink);
            g.fill(bx + 7, y + 2, bx + 8, y + 7, ink);
            g.fill(bx + 5, y + 12, bx + 10, y + 13, ink);
        }
        super.renderWidget(g, mouseX, mouseY, tick);
    }
}
