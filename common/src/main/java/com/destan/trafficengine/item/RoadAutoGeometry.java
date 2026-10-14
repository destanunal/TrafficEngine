package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;

/** Generates a connected stripe without adding thousands of baked block states. */
public final class RoadAutoGeometry {
    private static final RoadPaintPlan.Cell CELL = new RoadPaintPlan.Cell(0, 0, 0);
    private RoadAutoGeometry() {}

    public static double offset(Direction facing, int position) {
        return RoadStrokeGeometry.positionOffset(position)
                * (facing == Direction.NORTH || facing == Direction.EAST ? 1 : -1);
    }

    /** NaN means no neighbour. Other values are shared coordinates along the block edge. */
    public static float[] connected(Direction facing, int style, int position, double[] neighbours) {
        double[] ports = neighbours.clone();
        List<Direction> connected = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (Double.isFinite(ports[side.get2DDataValue()])) connected.add(side);
        if (connected.isEmpty()) {
            ports[facing.get2DDataValue()] = ports[facing.getOpposite().get2DDataValue()] = .5 + offset(facing, position);
            connected.add(facing); connected.add(facing.getOpposite());
        } else if (connected.size() == 1) {
            Direction side = connected.get(0);
            ports[side.getOpposite().get2DDataValue()] = ports[side.get2DDataValue()];
            connected.add(side.getOpposite());
        }
        Map<RoadPaintPlan.Cell, List<Float>> mesh = new LinkedHashMap<>();
        double width = RoadStrokeGeometry.lineWidth(style);
        double[] bands = style == 2 ? new double[]{-.1875, .1875} : new double[]{0};
        if (connected.size() == 2) {
            Direction a = connected.get(0), b = connected.get(1);
            var first = port(a, ports[a.get2DDataValue()]);
            var last = port(b, ports[b.get2DDataValue()]);
            if (a.getOpposite() == b) {
                if (style == 1) {
                    for (double[] run : new double[][]{{0, .125}, {.375, .625}, {.875, 1}})
                        strip(mesh, lerp(first, last, run[0]), lerp(first, last, run[1]), bands, width);
                } else strip(mesh, first, last, bands, width);
            } else {
                double cx = a == Direction.EAST || b == Direction.EAST ? 1 : 0;
                double cz = a == Direction.SOUTH || b == Direction.SOUTH ? 1 : 0;
                List<RoadStrokeGeometry.Point> path = new ArrayList<>();
                for (int i = 0; i <= 16; i++) {
                    if (i == 0) { path.add(first); continue; }
                    if (i == 16) { path.add(last); continue; }
                    double angle = i * Math.PI / 32;
                    path.add(new RoadStrokeGeometry.Point(cx + (first.x() - cx) * Math.cos(angle) + (last.x() - cx) * Math.sin(angle),
                            cz + (first.z() - cz) * Math.cos(angle) + (last.z() - cz) * Math.sin(angle)));
                }
                curvedStrip(mesh, path, bands, width);
            }
        } else {
            double x = mean(ports[Direction.NORTH.get2DDataValue()], ports[Direction.SOUTH.get2DDataValue()], .5 + offset(facing, position));
            double z = mean(ports[Direction.EAST.get2DDataValue()], ports[Direction.WEST.get2DDataValue()], .5 + offset(facing, position));
            var centre = new RoadStrokeGeometry.Point(x, z);
            for (Direction side : connected) {
                var edge = port(side, ports[side.get2DDataValue()]);
                if (style == 1) {
                    strip(mesh, centre, lerp(centre, edge, .25), bands, width);
                    strip(mesh, lerp(centre, edge, .75), edge, bands, width);
                } else strip(mesh, centre, edge, bands, width);
            }
        }
        return RoadStrokeGeometry.pack(mesh).getOrDefault(CELL, new float[0]);
    }

    private static double mean(double a, double b, double fallback) {
        return Double.isFinite(a) ? Double.isFinite(b) ? (a + b) / 2 : a : Double.isFinite(b) ? b : fallback;
    }
    private static RoadStrokeGeometry.Point port(Direction side, double coordinate) {
        return new RoadStrokeGeometry.Point(side == Direction.WEST ? 0 : side == Direction.EAST ? 1 : coordinate,
                side == Direction.NORTH ? 0 : side == Direction.SOUTH ? 1 : coordinate);
    }
    private static RoadStrokeGeometry.Point lerp(RoadStrokeGeometry.Point a, RoadStrokeGeometry.Point b, double t) {
        return new RoadStrokeGeometry.Point(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t);
    }
    private static void strip(Map<RoadPaintPlan.Cell, List<Float>> mesh, RoadStrokeGeometry.Point a,
            RoadStrokeGeometry.Point b, double[] bands, double width) {
        curvedStrip(mesh, List.of(a, b), bands, width);
    }
    private static void curvedStrip(Map<RoadPaintPlan.Cell, List<Float>> mesh, List<RoadStrokeGeometry.Point> path,
            double[] bands, double width) {
        for (double band : bands) {
            List<RoadStrokeGeometry.Point> left = new ArrayList<>(), right = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                var p = path.get(i);
                var before = path.get(Math.max(0, i - 1));
                var after = path.get(Math.min(path.size() - 1, i + 1));
                double dx = after.x() - before.x(), dz = after.z() - before.z();
                double length = Math.hypot(dx, dz), nx = -dz / length, nz = dx / length;
                // End tangents must meet the shared edge squarely. Chord tangents leave
                // a small gap at a rounded bend when the next cell is a straight stripe.
                if (i == 0 || i == path.size() - 1) {
                    if (p.x() == 0 || p.x() == 1) { nx = 0; nz = Math.signum(dx); }
                    else if (p.z() == 0 || p.z() == 1) { nx = -Math.signum(dz); nz = 0; }
                }
                var a = new RoadStrokeGeometry.Point(p.x() + nx * (band + width / 2), p.z() + nz * (band + width / 2));
                var b = new RoadStrokeGeometry.Point(p.x() + nx * (band - width / 2), p.z() + nz * (band - width / 2));
                // A tight inner double stripe can fold across a second block edge.
                // Only the two intended end ports may reach that edge.
                if (path.size() > 2 && i > 0 && i < path.size() - 1) { a = interior(a); b = interior(b); }
                left.add(a); right.add(b);
            }
            for (int i = 0; i < path.size() - 1; i++)
                RoadStrokeGeometry.appendPolygon(mesh, 0, List.of(left.get(i), left.get(i + 1), right.get(i + 1), right.get(i)), true, 4096);
        }
    }
    private static RoadStrokeGeometry.Point interior(RoadStrokeGeometry.Point p) {
        double inset = 1D / 4096;
        return new RoadStrokeGeometry.Point(Math.max(inset, Math.min(1 - inset, p.x())),
                Math.max(inset, Math.min(1 - inset, p.z())));
    }
}
