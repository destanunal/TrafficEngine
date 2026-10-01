package com.destan.trafficengine.client.gui.components;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Integer field that allows intermediate typing without overwriting the last valid setting. */
public class TENumberBox extends EditBox {
    private final IntPredicate validator;
    public TENumberBox(Font font, int x, int y, int width, int value, Component label,
            IntPredicate validator, IntConsumer change) {
        super(font, x, y, width, 18, label);
        this.validator = validator;
        setMaxLength(5);
        setFilter(text -> text.matches("-?[0-9]{0,4}"));
        setTextColor(TEColors.TEXT);
        setValue(Integer.toString(value));
        setResponder(text -> {
            if (isValid()) change.accept(Integer.parseInt(text));
        });
    }
    public boolean isValid() {
        String text = getValue();
        int start = text.startsWith("-") ? 1 : 0;
        if (text.length() == start) return false;
        int value = 0;
        for (int i = start; i < text.length(); i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') return false;
            value = value * 10 + digit - '0';
        }
        if (start == 1) value = -value;
        return value >= -9999 && value <= 9999 && validator.test(value);
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (!isValid()) TEPanel.outline(g, getX(), getY(), width, height, 0xFFE86A63);
    }
}
