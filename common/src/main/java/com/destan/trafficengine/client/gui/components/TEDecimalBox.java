package com.destan.trafficengine.client.gui.components;

import java.util.function.DoubleConsumer;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Decimal seconds retain the schedule's tick precision and accept intermediate typing. */
public class TEDecimalBox extends TEEditBox {
    private final double max;
    public TEDecimalBox(Font font, int x, int y, int width, double value, double max, Component label, DoubleConsumer change) {
        super(font, x, y, width, 18, label);
        this.max = max;
        setMaxLength(8);
        setFilter(text -> text.matches("[0-9]{0,3}(\\.[0-9]{0,2})?"));
        setTextColor(TEColors.TEXT);
        setValue(value == (int)value ? Integer.toString((int)value) : Double.toString(value));
        setResponder(text -> { if (isValid()) change.accept(Double.parseDouble(text)); });
    }
    public boolean isValid() {
        try { double value = Double.parseDouble(getValue()); return Double.isFinite(value) && value >= 0 && value <= max; }
        catch (NumberFormatException ex) { return false; }
    }
    @Override protected int frameColor() {
        return isValid() ? super.frameColor() : 0xFFE86A63;
    }
}
