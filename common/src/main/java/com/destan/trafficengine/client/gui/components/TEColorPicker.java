package com.destan.trafficengine.client.gui.components;

import java.util.function.IntConsumer;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Saturation/value plane and hue strip, drawn without images or a texture allocation. */
public final class TEColorPicker extends AbstractWidget {
    private static final int HUE_WIDTH = 12, GAP = 8;
    private final IntConsumer change;
    private float hue, saturation, brightness;
    private boolean draggingHue;

    public TEColorPicker(int x, int y, int width, int height, int color, Component label, IntConsumer change) {
        super(x, y, width, height, label);
        this.change = change;
        setColor(color);
    }

    public void setColor(int color) {
        float red = ((color >>> 16) & 255) / 255F;
        float green = ((color >>> 8) & 255) / 255F;
        float blue = (color & 255) / 255F;
        float max = Math.max(red, Math.max(green, blue)), min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        brightness = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta > 0) {
            float sector = max == red ? (green - blue) / delta : max == green ? (blue - red) / delta + 2 : (red - green) / delta + 4;
            hue = (sector / 6 + 1) % 1;
        }
    }

    private int planeWidth() { return width - HUE_WIDTH - GAP; }
    private void update(double x, double y) {
        if (draggingHue) hue = Mth.clamp((float)(y - getY()) / (height - 1), 0, 1);
        else {
            saturation = Mth.clamp((float)(x - getX()) / (planeWidth() - 1), 0, 1);
            brightness = 1 - Mth.clamp((float)(y - getY()) / (height - 1), 0, 1);
        }
        publish();
    }
    private void publish() { change.accept(0xFF000000 | Mth.hsvToRgb(hue, saturation, brightness)); }
    @Override public void onClick(double x, double y) {
        draggingHue = x >= getX() + planeWidth() + GAP;
        update(x, y);
    }
    @Override protected void onDrag(double x, double y, double dx, double dy) { update(x, y); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!active || !isFocused()) return false;
        float direction = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1 : 1;
        if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT && key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) hue = (hue + direction / 60F + 1) % 1;
        else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) saturation = Mth.clamp(saturation + direction / 100F, 0, 1);
        else brightness = Mth.clamp(brightness - direction / 100F, 0, 1);
        publish();
        return true;
    }
    @Override public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int plane = planeWidth(), hx = getX() + plane + GAP;
        // White-to-hue horizontally, then transparent-to-black vertically: exact HSV in two quads.
        int pureHue = Mth.hsvToRgb(hue, 1, 1);
        var matrix = g.pose().last().pose();
        var vertices = g.bufferSource().getBuffer(RenderType.gui());
        int red = (pureHue >>> 16) & 255, green = (pureHue >>> 8) & 255, blue = pureHue & 255;
        vertices.addVertex(matrix, getX(), getY(), 0).setColor(255, 255, 255, 255);
        vertices.addVertex(matrix, getX(), getY() + height, 0).setColor(255, 255, 255, 255);
        vertices.addVertex(matrix, getX() + plane, getY() + height, 0).setColor(red, green, blue, 255);
        vertices.addVertex(matrix, getX() + plane, getY(), 0).setColor(red, green, blue, 255);
        g.fillGradient(getX(), getY(), getX() + plane, getY() + height, 0x00000000, 0xFF000000);
        for (int i = 0; i < 6; i++) {
            g.fillGradient(hx, getY() + i * height / 6, hx + HUE_WIDTH, getY() + (i + 1) * height / 6,
                0xFF000000 | Mth.hsvToRgb(i / 6F, 1, 1), 0xFF000000 | Mth.hsvToRgb((i + 1) / 6F, 1, 1));
        }
        TEPanel.outline(g, getX() - 1, getY() - 1, plane + 2, height + 2, isFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
        TEPanel.outline(g, hx - 1, getY() - 1, HUE_WIDTH + 2, height + 2, TEColors.BORDER);
        int cx = getX() + Math.round(saturation * (plane - 1)), cy = getY() + Math.round((1 - brightness) * (height - 1));
        TEPanel.outline(g, cx - 3, cy - 3, 7, 7, TEColors.INK);
        TEPanel.outline(g, cx - 2, cy - 2, 5, 5, TEColors.TEXT);
        int hy = getY() + Math.round(hue * (height - 1));
        g.fill(hx - 2, hy - 2, hx + HUE_WIDTH + 2, hy + 3, TEColors.INK);
        g.fill(hx - 1, hy - 1, hx + HUE_WIDTH + 1, hy + 2, TEColors.TEXT);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
}
