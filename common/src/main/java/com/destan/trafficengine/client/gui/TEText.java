package com.destan.trafficengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Keep menu glyph strokes on whole framebuffer pixels, without drawing captions twice. */
public final class TEText {
    private TEText() {}

    static float pixelScale(float scale, double guiScale) {
        if (scale < .5F || scale > 1.5F) return scale; // Preserve small sign/model previews.
        return (float)(Math.max(1, Math.round(scale * guiScale)) / guiScale);
    }
    private static boolean aligned(Matrix4f pose) {
        return pose.m00() > 0 && pose.m11() > 0 && pose.m01() == 0 && pose.m10() == 0;
    }
    public static float layoutScale(GuiGraphics g) {
        Matrix4f pose = g.pose().last().pose();
        return aligned(pose) ? pixelScale(pose.m00(), Minecraft.getInstance().getWindow().getGuiScale()) / pose.m00() : 1;
    }
    static void position(Matrix4f pose, float x, float y, double pixels) {
        pose.m30((float)(Math.round((pose.m30() + x * pose.m00()) * pixels) / pixels));
        pose.m31((float)(Math.round((pose.m31() + y * pose.m11()) * pixels) / pixels));
        pose.m00(pixelScale(pose.m00(), pixels)); pose.m11(pixelScale(pose.m11(), pixels));
    }
    public static int drawString(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        Matrix4f pose = g.pose().last().pose();
        if (!aligned(pose)) return g.drawString(font, text, x, y, color, shadow);
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m30(), ty = pose.m31();
        position(pose, x, y, Minecraft.getInstance().getWindow().getGuiScale());
        try { return g.drawString(font, text, 0, 0, color, shadow); }
        finally { pose.m00(sx).m11(sy).m30(tx).m31(ty); }
    }
    public static int drawString(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        return drawString(g, font, text.getVisualOrderText(), x, y, color, shadow);
    }
    public static int drawString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        Matrix4f pose = g.pose().last().pose();
        if (!aligned(pose)) return g.drawString(font, text, x, y, color, shadow);
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m30(), ty = pose.m31();
        position(pose, x, y, Minecraft.getInstance().getWindow().getGuiScale());
        try { return g.drawString(font, text, 0, 0, color, shadow); }
        finally { pose.m00(sx).m11(sy).m30(tx).m31(ty); }
    }
    public static void drawCenteredString(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        drawString(g, font, text, x - Math.round(font.width(text) * layoutScale(g) / 2), y, color, false);
    }
    public static void drawCenteredString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        drawString(g, font, text, x - Math.round(font.width(text) * layoutScale(g) / 2), y, color, false);
    }
    public static void drawCenteredString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        drawString(g, font, text, x - Math.round(font.width(text) * layoutScale(g) / 2), y, color, false);
    }
    public static void drawWordWrap(GuiGraphics g, Font font, FormattedText text, int x, int y, int width, int color) {
        float scale = layoutScale(g);
        for (FormattedCharSequence line : font.split(text, Math.max(1, (int)(width / scale)))) {
            drawString(g, font, line, x, y, color, false);
            y += Math.round(font.lineHeight * scale);
        }
    }
}
