package com.destan.trafficengine.item;

import java.util.function.IntPredicate;

/** Resolves a bounded path onto the exposed road rather than a fixed horizontal plane. */
public final class RoadPaintSurface {
    private RoadPaintSurface() {}

    public static Integer height(RoadPaintPlan.Cell cell, RoadPaintPlan.Cell start,
            RoadPaintPlan.Cell end, IntPredicate isSurface) {
        long dx = (long) end.x() - start.x(), dz = (long) end.z() - start.z();
        long dy = (long) end.y() - start.y();
        if (Math.abs(dy) > 64 || Math.max(Math.abs(dx), Math.abs(dz)) > RoadPaintPlan.MAX_DISTANCE) return null;
        double lengthSquared = (double) dx * dx + (double) dz * dz;
        double progress = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
                (((double) cell.x() - start.x()) * dx + ((double) cell.z() - start.z()) * dz) / lengthSquared));
        int expected = start.y() + (int) Math.round(dy * progress);
        int low = Math.min(start.y(), end.y()) - 1, high = Math.max(start.y(), end.y()) + 1;
        for (int offset = 0; offset <= high - low; offset++) {
            int above = expected + offset;
            if (above >= low && above <= high && isSurface.test(above)) return above;
            int below = expected - offset;
            if (offset > 0 && below >= low && below <= high && isSurface.test(below)) return below;
        }
        return null;
    }
}
