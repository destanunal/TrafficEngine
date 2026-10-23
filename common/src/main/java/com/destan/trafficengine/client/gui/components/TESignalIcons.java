package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.block.data.TrafficLightIcon;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/** Neutral, consistent signal outlines. Geometry is prepared once; no texture or frame allocation. */
public final class TESignalIcons {
    private static final float[][] ICONS = createIcons();
    private TESignalIcons() {}

    public static void draw(GuiGraphics g, TrafficLightIcon icon, int x, int y, int color) {
        draw(g, ICONS[icon.ordinal()], x, y, color);
    }
    private static void draw(GuiGraphics g, float[] quads, int x, int y, int color) {
        if (quads.length == 0) return;
        var matrix = g.pose().last().pose();
        var vertices = g.bufferSource().getBuffer(RenderType.gui());
        int r = color >>> 16 & 255, green = color >>> 8 & 255, b = color & 255, a = color >>> 24;
        for (int i = 0; i < quads.length; i += 2) {
            float px = x + quads[i], py = y + quads[i + 1];
            vertices.vertex(matrix.m00()*px + matrix.m10()*py + matrix.m30(),
                    matrix.m01()*px + matrix.m11()*py + matrix.m31(),
                    matrix.m02()*px + matrix.m12()*py + matrix.m32())
                .color(r, green, b, a).endVertex();
        }
    }
    private static float[][] createIcons() {
        float[][] result = new float[TrafficLightIcon.values().length][];
        result[TrafficLightIcon.NONE.ordinal()] = new float[0];
        float[] right = new Builder().line(3, 8, 12, 8, 2).line(8, 4, 12, 8, 2).line(12, 8, 8, 12, 2).build();
        result[TrafficLightIcon.RIGHT.ordinal()] = right;
        result[TrafficLightIcon.LEFT.ordinal()] = mirror(right);
        result[TrafficLightIcon.STRAIGHT.ordinal()] = new Builder().line(8, 13, 8, 3, 2)
            .line(4, 7, 8, 3, 2).line(8, 3, 12, 7, 2).build();
        float[] branch = new Builder().line(5.5, 13, 5.5, 3, 1.8).line(2.5, 6, 5.5, 3, 1.8)
            .line(5.5, 3, 8.5, 6, 1.8).line(5.5, 9, 13, 9, 1.8)
            .line(10, 6, 13, 9, 1.8).line(13, 9, 10, 12, 1.8).build();
        result[TrafficLightIcon.STRAIGHT_RIGHT.ordinal()] = branch;
        result[TrafficLightIcon.STRAIGHT_LEFT.ordinal()] = mirror(branch);
        result[TrafficLightIcon.PEDESTRIAN.ordinal()] = new Builder().disc(8.5, 3, 1.5)
            .line(8, 5.5, 7, 9.5, 1.8).line(7.8, 6, 4.5, 8, 1.7).line(7.8, 6, 11.5, 8, 1.7)
            .line(7, 9, 4.5, 13, 1.8).line(7, 9, 10.5, 13, 1.8).build();
        result[TrafficLightIcon.BIKE.ordinal()] = new Builder().ring(4, 11, 2.7, 1.35).ring(12, 11, 2.7, 1.35)
            .line(4, 11, 7, 6, 1.3).line(7, 6, 9, 11, 1.3).line(9, 11, 4, 11, 1.3)
            .line(7, 6, 10.5, 6, 1.3).line(9, 11, 10.5, 6, 1.3).line(10.5, 6, 12, 11, 1.3)
            .line(10.5, 6, 10, 3.5, 1.3).line(10, 3.5, 13, 3.5, 1.3).line(6, 5.2, 8, 5.2, 1.3).build();
        return result;
    }
    private static float[] mirror(float[] source) {
        float[] result = new float[source.length];
        for (int quad = 0; quad < source.length; quad += 8) for (int vertex = 0; vertex < 4; vertex++) {
            int from = quad + (3-vertex)*2, to = quad + vertex*2;
            result[to] = 16-source[from]; result[to+1] = source[from+1];
        }
        return result;
    }
    private static final class Builder {
        private final FloatArrayList points = new FloatArrayList();
        private void quad(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
            points.add((float)ax); points.add((float)ay); points.add((float)bx); points.add((float)by);
            points.add((float)cx); points.add((float)cy); points.add((float)dx); points.add((float)dy);
        }
        Builder line(double ax, double ay, double bx, double by, double width) {
            double length = Math.hypot(bx-ax, by-ay), nx = -(by-ay)*width/(2*length), ny = (bx-ax)*width/(2*length);
            quad(ax+nx, ay+ny, bx+nx, by+ny, bx-nx, by-ny, ax-nx, ay-ny);
            return this;
        }
        Builder ring(double x, double y, double radius, double width) {
            double inner = radius-width/2, outer = radius+width/2;
            for (int i = 0; i < 16; i++) {
                double a = i*Math.PI/8, b = (i+1)*Math.PI/8;
                quad(x+Math.cos(a)*outer, y+Math.sin(a)*outer, x+Math.cos(a)*inner, y+Math.sin(a)*inner,
                    x+Math.cos(b)*inner, y+Math.sin(b)*inner, x+Math.cos(b)*outer, y+Math.sin(b)*outer);
            }
            return this;
        }
        Builder disc(double x, double y, double radius) {
            for (int i = 0; i < 12; i++) {
                double a = i*Math.PI/6, b = (i+1)*Math.PI/6;
                double ax = x+Math.cos(a)*radius, ay = y+Math.sin(a)*radius;
                quad(x, y, x+Math.cos(b)*radius, y+Math.sin(b)*radius, ax, ay, ax, ay);
            }
            return this;
        }
        float[] build() { return points.toFloatArray(); }
    }
}
