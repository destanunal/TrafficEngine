package com.destan.trafficengine.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Clips baked faces at the supporting block's top, retaining interpolated texture UVs. */
public final class GroundPlaneQuadClipper {
    private GroundPlaneQuadClipper() {}

    public static List<int[]> clip(int[] packed) {
        int stride = packed.length / 4;
        List<int[]> polygon = new ArrayList<>(4);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            int[] vertex = Arrays.copyOfRange(packed, i * stride, (i + 1) * stride);
            float y = value(vertex, 1);
            // Rotating a toe onto y=0 can leave a tiny floating-point remainder.
            if (y != 0 && Math.abs(y) < 1.0e-7f) {
                vertex[1] = Float.floatToRawIntBits(0);
                changed = true;
            }
            changed |= value(vertex, 1) < 0;
            polygon.add(vertex);
        }
        if (!changed) return List.of(packed);

        List<int[]> clipped = new ArrayList<>(5);
        int[] previous = polygon.get(3);
        for (int[] current : polygon) {
            boolean previousInside = value(previous, 1) >= 0;
            boolean currentInside = value(current, 1) >= 0;
            if (previousInside != currentInside) clipped.add(intersection(previous, current));
            if (currentInside) clipped.add(current);
            previous = current;
        }
        if (clipped.size() < 3) return List.of();
        if (clipped.size() <= 4) {
            return List.of(pack(clipped.get(0), clipped.get(1), clipped.get(2),
                    clipped.get(clipped.size() - 1)));
        }
        List<int[]> result = new ArrayList<>();
        for (int i = 1; i < clipped.size() - 1; i++) {
            result.add(pack(clipped.get(0), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1)));
        }
        return List.copyOf(result);
    }

    private static int[] intersection(int[] a, int[] b) {
        int[] result = a.clone();
        float t = -value(a, 1) / (value(b, 1) - value(a, 1));
        for (int component : new int[] {0, 2, 4, 5}) {
            result[component] = Float.floatToRawIntBits(value(a, component)
                    + t * (value(b, component) - value(a, component)));
        }
        result[1] = Float.floatToRawIntBits(0);
        return result;
    }

    private static float value(int[] vertex, int index) {
        return Float.intBitsToFloat(vertex[index]);
    }

    private static int[] pack(int[] a, int[] b, int[] c, int[] d) {
        int stride = a.length;
        int[] result = new int[stride * 4];
        int[][] vertices = {a, b, c, d};
        for (int i = 0; i < 4; i++) System.arraycopy(vertices[i], 0, result, i * stride, stride);
        return result;
    }
}
