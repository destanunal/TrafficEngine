package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Ordered, unique road cells sampled from block centres, including the final endpoint. */
public final class RoadConstructionPlan {
    public record Cell(int x, int y, int z) {}

    private RoadConstructionPlan() {}

    public static List<Map<Cell, Integer>> calculate(double sx, double sy, double sz,
            double ex, double ey, double ez, int width, Predicate<Cell> canPlace) {
        if (width < 1 || !Double.isFinite(sx + sy + sz + ex + ey + ez)) return List.of();
        if (sy > ey) {
            double x = sx, y = sy, z = sz;
            sx = ex; sy = ey; sz = ez;
            ex = x; ey = y; ez = z;
        }
        sx += 0.5; sz += 0.5;
        ex += 0.5; ez += 0.5;
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-7) return List.of();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(1, (int)Math.ceil(length * 2));
        double rightX = dz / horizontal, rightZ = -dx / horizontal;
        double halfWidth = (width - 1) / 2.0;
        List<Map<Cell, Integer>> result = new ArrayList<>();
        Map<Cell, Map<Cell, Integer>> owners = new HashMap<>();
        for (int sample = 0; sample <= samples; sample++) {
            double t = (double)sample / samples;
            double x = sx + dx * t, y = sy + dy * t, z = sz + dz * t;
            int surfaceY = (int)Math.floor(y + 1.0E-7);
            int layers = Math.max(0, Math.min(7, (int)Math.floor((y - surfaceY) * 8 + 1.0E-7)));
            Map<Cell, Integer> slice = new LinkedHashMap<>();
            for (double offset = -halfWidth; offset <= halfWidth; offset += 0.5) {
                int bx = (int)Math.floor(x + rightX * offset);
                int bz = (int)Math.floor(z + rightZ * offset);
                add(new Cell(bx, surfaceY - 1, bz), 8, slice, owners, canPlace);
                if (layers > 0) add(new Cell(bx, surfaceY, bz), layers, slice, owners, canPlace);
            }
            if (!slice.isEmpty()) result.add(slice);
        }
        return result;
    }

    private static void add(Cell cell, int height, Map<Cell, Integer> slice,
            Map<Cell, Map<Cell, Integer>> owners, Predicate<Cell> canPlace) {
        Map<Cell, Integer> owner = owners.get(cell);
        if (owner != null) {
            owner.merge(cell, height, Math::max);
        } else if (canPlace.test(cell)) {
            slice.put(cell, height);
            owners.put(cell, slice);
        }
    }
}
