package com.destan.trafficengine.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable 16 x 16 stencil: 32 bytes on disk and on the wire. */
public final class PixelPattern {
    public static final int SIZE = 16;
    public static final PixelPattern EMPTY = new PixelPattern(new long[4]);
    public record Rect(int x, int y, int width, int height) {}
    private final long[] bits;
    private final List<Rect> rectangles;
    private final int hash;

    private PixelPattern(long[] bits) {
        this.bits = bits;
        hash = Arrays.hashCode(bits);
        boolean[] used = new boolean[256];
        List<Rect> result = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (used[y * SIZE + x] || !pixel(x, y)) continue;
            int width = 1, height = 1;
            while (x + width < SIZE && !used[y * SIZE + x + width] && pixel(x + width, y)) width++;
            outer: while (y + height < SIZE) {
                for (int dx = 0; dx < width; dx++)
                    if (used[(y + height) * SIZE + x + dx] || !pixel(x + dx, y + height)) break outer;
                height++;
            }
            for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++)
                used[(y + dy) * SIZE + x + dx] = true;
            result.add(new Rect(x, y, width, height));
        }
        rectangles = List.copyOf(result);
    }
    public static PixelPattern of(long[] bits) {
        if (bits == null || bits.length != 4) return EMPTY;
        if ((bits[0] | bits[1] | bits[2] | bits[3]) == 0) return EMPTY;
        return new PixelPattern(bits.clone());
    }
    public boolean pixel(int x, int y) {
        int index = y * SIZE + x;
        return (bits[index >>> 6] & (1L << (index & 63))) != 0;
    }
    public boolean isEmpty() { return rectangles.isEmpty(); }
    public long[] bits() { return bits.clone(); }
    public List<Rect> rectangles() { return rectangles; }
    public float[] roadVertices(float height, int turns) {
        float[] mesh = new float[rectangles.size() * 12];
        int out = 0;
        for (Rect rect : rectangles) {
            float minX = rect.x / 16F, maxX = (rect.x + rect.width) / 16F;
            float minZ = rect.y / 16F, maxZ = (rect.y + rect.height) / 16F;
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = vertex < 2 ? minX : maxX;
                float z = vertex == 0 || vertex == 3 ? minZ : maxZ;
                for (int turn = 0; turn < (turns & 3); turn++) { float oldX = x; x = 1 - z; z = oldX; }
                mesh[out++] = x; mesh[out++] = height; mesh[out++] = z;
            }
        }
        return mesh;
    }
    @Override public boolean equals(Object other) { return other instanceof PixelPattern pattern && Arrays.equals(bits, pattern.bits); }
    @Override public int hashCode() { return hash; }
}
