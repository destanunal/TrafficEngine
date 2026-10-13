package com.destan.trafficengine.item;

import java.util.LinkedHashSet;
import java.util.List;

/** A bounded, ordered four-connected stroke, including both selected endpoints. */
public final class RoadPaintPlan {
    public record Cell(int x, int y, int z) {}
    public static final int MAX_CELLS = 1024;
    public static final int MAX_DISTANCE = 256;
    private RoadPaintPlan() {}
    public static List<Cell> between(Cell start, Cell end, int width) {
        long deltaX = (long)end.x - start.x, deltaZ = (long)end.z - start.z;
        long deltaY = (long)end.y - start.y;
        if ((deltaX == 0 && deltaZ == 0 && deltaY != 0) || Math.max(Math.abs(deltaX), Math.abs(deltaZ)) > MAX_DISTANCE
                || Math.abs(deltaY) > 64 || width < 1 || width > 9 || width % 2 == 0) return List.of();
        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        int dx = (int)Math.abs(deltaX), dz = (int)Math.abs(deltaZ);
        int sx = deltaX < 0 ? -1 : 1, sz = deltaZ < 0 ? -1 : 1;
        int x = start.x, z = start.z, error = dx - dz;
        int steps = Math.max(1, Math.max(dx, dz)), step = 0;
        boolean wideAlongX = dz >= dx;
        while (true) {
            int y = start.y + (int)Math.round((double)deltaY * step / steps);
            add(cells, x, y, z, width, wideAlongX);
            if (cells.size() > MAX_CELLS) return List.of();
            if (x == end.x && z == end.z) break;
            int oldX = x, oldZ = z, twice = 2 * error;
            boolean moveX = twice > -dz, moveZ = twice < dx;
            if (moveX) { error -= dz; x += sx; }
            if (moveZ) { error += dx; z += sz; }
            if (moveX && moveZ) add(cells, x, y, oldZ, width, wideAlongX);
            step++;
        }
        return List.copyOf(cells);
    }
    private static void add(LinkedHashSet<Cell> cells, int x, int y, int z, int width, boolean alongX) {
        for (int offset = -width / 2; offset <= width / 2; offset++)
            cells.add(new Cell(x + (alongX ? offset : 0), y, z + (alongX ? 0 : offset)));
    }
}
