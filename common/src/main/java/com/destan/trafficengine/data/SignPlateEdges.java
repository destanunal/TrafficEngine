package com.destan.trafficengine.data;

import java.util.ArrayList;
import java.util.List;

/** Contiguous edge runs of a sign's alpha mask, in its normalized front coordinates. */
public final class SignPlateEdges {
    public record Edge(float x0, float y0, float x1, float y1, float u, float v, float shade) {}

    private SignPlateEdges() {}

    public static List<Edge> fromMask(int[] rows, int width) {
        int height = rows.length;
        List<Edge> edges = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            int above = y == 0 ? 0 : rows[y - 1];
            int below = y + 1 == height ? 0 : rows[y + 1];
            appendRuns(edges, rows[y] & ~above, y, -1, false, width, height);
            appendRuns(edges, rows[y] & ~below, y, 1, false, width, height);
        }
        int previous = 0;
        int column = column(rows, 0);
        for (int x = 0; x < width; x++) {
            int next = x + 1 == width ? 0 : column(rows, x + 1);
            appendRuns(edges, column & ~previous, x, -1, true, height, width);
            appendRuns(edges, column & ~next, x, 1, true, height, width);
            previous = column;
            column = next;
        }
        return List.copyOf(edges);
    }

    private static int column(int[] rows, int x) {
        int bits = 0;
        for (int y = 0; y < rows.length; y++) bits |= (rows[y] >>> x & 1) << y;
        return bits;
    }

    private static void appendRuns(List<Edge> edges, int bits, int line, int side, boolean vertical,
            int along, int across) {
        while (bits != 0) {
            int first = Integer.numberOfTrailingZeros(bits);
            int end = first + Integer.numberOfTrailingZeros(~(bits >>> first));
            // A run ending at pixel 32 cannot be cleared with a shift by 32.
            bits = end == Integer.SIZE ? 0 : bits & (-1 << end);

            float a = first / (float)along, b = end / (float)along;
            float edge = (line + (side > 0 ? 1 : 0)) / (float)across;
            float sample = (first + 0.5f) / along;
            float middle = (line + 0.5f) / across;
            if (vertical) {
                edges.add(side < 0 ? new Edge(edge, b, edge, a, 1 - middle, sample, 0.8f)
                    : new Edge(edge, a, edge, b, 1 - middle, sample, 0.8f));
            } else {
                edges.add(side < 0 ? new Edge(a, edge, b, edge, 1 - sample, middle, 1)
                    : new Edge(b, edge, a, edge, 1 - sample, middle, 0.7f));
            }
        }
    }
}
