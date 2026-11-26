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
    public static float centeredY(GuiGraphics g, Font font, Object caption, int top, int height) {
        return MenuTextBounds.centeredExact(top,height,layoutScale(g),MenuTextBounds.of(font,caption));
    }
    public static int width(Font font,Object caption) { return MenuTextBounds.of(font,caption).width(); }
    public static float centeredLinesY(GuiGraphics g,Font font,java.util.List<FormattedCharSequence> lines,int top,int height) {
        if(lines.isEmpty())return top+height/2;
        float scale=layoutScale(g);int step=Math.round(font.lineHeight*scale);
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int i=0;i<lines.size();i++) {
            var bounds=MenuTextBounds.of(font,lines.get(i));
            min=Math.min(min,i*step+bounds.top()*scale);max=Math.max(max,i*step+bounds.bottom()*scale);
        }
        return top+(height-(max-min))/2F-min;
    }
    public static int drawString(GuiGraphics g, Font font, String text, float x, float y, int color, boolean shadow) {
        Matrix4f pose = g.pose().last().pose();
        if (!aligned(pose)) return g.drawString(font, text, Math.round(x), Math.round(y), color, shadow);
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m30(), ty = pose.m31();
        position(pose, x, y, Minecraft.getInstance().getWindow().getGuiScale());
        try { return g.drawString(font, text, 0, 0, color, shadow); }
        finally { pose.m00(sx).m11(sy).m30(tx).m31(ty); }
    }
    public static int drawString(GuiGraphics g, Font font, Component text, float x, float y, int color, boolean shadow) {
        return drawString(g, font, text.getVisualOrderText(), x, y, color, shadow);
    }
    public static int drawString(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        Matrix4f pose = g.pose().last().pose();
        if (!aligned(pose)) return g.drawString(font, text, Math.round(x), Math.round(y), color, shadow);
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m30(), ty = pose.m31();
        position(pose, x, y, Minecraft.getInstance().getWindow().getGuiScale());
        try { return g.drawString(font, text, 0, 0, color, shadow); }
        finally { pose.m00(sx).m11(sy).m30(tx).m31(ty); }
    }
    public static void drawCenteredString(GuiGraphics g, Font font, Component text, float x, float y, int color) {
        drawString(g, font, text, x - width(font,text) * layoutScale(g) / 2F, y, color, false);
    }
    public static void drawCenteredString(GuiGraphics g, Font font, String text, float x, float y, int color) {
        drawString(g, font, text, x - width(font,text) * layoutScale(g) / 2F, y, color, false);
    }
    public static void drawCenteredString(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int color) {
        drawString(g, font, text, x - width(font,text) * layoutScale(g) / 2F, y, color, false);
    }
    public static void drawWrappedCentered(GuiGraphics g, Font font, Component caption,
            int x, int y, int width, int height, int color) {
        drawWrapped(g, font, caption, x, y, width, height, color, true);
    }

    public static void drawWrappedLeft(GuiGraphics g, Font font, Component caption,
            int x, int y, int width, int height, int color) {
        drawWrapped(g, font, caption, x, y, width, height, color, false);
    }

    private static void drawWrapped(GuiGraphics g, Font font, Component caption,
            int x, int y, int width, int height, int color, boolean centered) {
        if (width < 1 || height < 1) return;
        var bounds = MenuTextBounds.of(font, caption);
        float scale = layoutScale(g);
        if (bounds.width() == 0 && !bounds.lineBreak()) return;
        if (!bounds.lineBreak() && bounds.width() * scale <= width && (bounds.bottom() - bounds.top()) * scale <= height) {
            drawString(g, font, caption, centered ? x + (width - bounds.width() * scale) / 2 : x,
                MenuTextBounds.centeredExact(y, height, scale, bounds), color, false);
            return;
        }

        // Fixed-height controls can fit several lines by scaling the whole paragraph if needed.
        int wrapWidth = Math.max((int)(width / scale), (int)Math.ceil(bounds.width() / 3.0));
        var layout = MenuTextBounds.wrapped(font, caption, Math.max(1, wrapWidth));
        if (layout.lines().isEmpty()) return;
        float inkHeight = Math.max(1, layout.bottom() - layout.top());
        float fittedScale = Math.min(scale, Math.min(width / (float)Math.max(1, layout.widest()), height / inkHeight));
        float firstY = y + (height - inkHeight * fittedScale) / 2 - layout.top() * fittedScale;
        for (int i = 0; i < layout.lines().size(); i++) {
            var line = layout.lines().get(i);
            float lineX = centered ? x + (width - line.width() * fittedScale) / 2 : x;
            float lineY = firstY + i * font.lineHeight * fittedScale;
            if (fittedScale == scale) drawString(g, font, line.text(), lineX, lineY, color, false);
            else drawFittedLine(g, font, line.text(), lineX, lineY, fittedScale, color);
        }
    }

    private static void drawFittedLine(GuiGraphics g, Font font, FormattedCharSequence text,
            float x, float y, float scale, int color) {
        Matrix4f pose = g.pose().last().pose();
        if (!aligned(pose)) return;
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m30(), ty = pose.m31();
        double pixels = Minecraft.getInstance().getWindow().getGuiScale();
        pose.m30((float)(Math.round((tx + x * sx) * pixels) / pixels));
        pose.m31((float)(Math.round((ty + y * sy) * pixels) / pixels));
        pose.m00(sx * scale).m11(sy * scale);
        try { g.drawString(font, text, 0, 0, color, false); }
        finally { pose.m00(sx).m11(sy).m30(tx).m31(ty); }
    }

    public static void drawWordWrap(GuiGraphics g, Font font, FormattedText text, int x, int y, int width, int color) {
        float scale = layoutScale(g);
        for (FormattedCharSequence line : font.split(text, Math.max(1, (int)(width / scale)))) {
            drawString(g, font, line, x, y, color, false);
            y += Math.round(font.lineHeight * scale);
        }
    }
}
