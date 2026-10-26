package com.destan.trafficengine.data;

import java.util.function.IntConsumer;

/** Bounded square pixel brush. Work happens only in input events. */
public final class PixelBrush {
    private final int maximum;
    private int size = 1;
    public PixelBrush(int maximum) { this.maximum = Math.max(1, maximum); }
    public int size() { return size; }
    public void setSize(int value) { size = Math.max(1, Math.min(maximum, value)); }
    public int start(int center) { return center - (size - 1) / 2; }
    public void stamp(int x, int y, int width, IntConsumer pixel) {
        int left = start(x), top = start(y);
        for (int py = Math.max(0, top); py < Math.min(width, top + size); py++)
            for (int px = Math.max(0, left); px < Math.min(width, left + size); px++)
                pixel.accept(py * width + px);
    }
    public void stroke(int x, int y, int toX, int toY, int width, IntConsumer pixel) {
        int dx = Math.abs(toX - x), dy = -Math.abs(toY - y);
        int sx = x < toX ? 1 : -1, sy = y < toY ? 1 : -1, error = dx + dy;
        while (true) {
            stamp(x, y, width, pixel);
            if (x == toX && y == toY) return;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x += sx; }
            if (twice <= dx) { error += dx; y += sy; }
        }
    }
}
