package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.destan.trafficengine.block.AsphaltSlope;
import com.destan.trafficengine.block.ConnectedRoadBlock;
import com.destan.trafficengine.block.ConnectedRoadSlopeBlock;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable chunk-mesh data, computed from road surfaces and their current neighbours. */
public final class RoadMarkingMesh {
    public static final float LIFT = .003125F;
    private RoadMarkingMesh() {}
    private record Surface(BlockPos pos, BlockState state, double height) {}

    public static boolean connectedRoad(BlockState state) {
        return state.getBlock() instanceof ConnectedRoadBlock || state.getBlock() instanceof ConnectedRoadSlopeBlock;
    }
    public static double height(BlockState state) {
        return state.hasProperty(AsphaltSlope.LAYERS) ? state.getValue(AsphaltSlope.LAYERS) / 8D : 1;
    }
    public static int automaticPosition(BlockState state, ColoredBlockEntity entity) {
        int position = entity.getAutomaticLinePosition();
        if (position >= 0) return position;
        if (state.getBlock() instanceof ConnectedRoadBlock && !state.getValue(ConnectedRoadBlock.STROKE)) return 0;
        return position == -2 ? legacyPosition(state, entity.getStrokeVertices()) : -1;
    }
    /** Recover straight single-block markings saved before the automatic-position field existed. */
    public static int legacyPosition(BlockState state, float[] vertices) {
        if (!connectedRoad(state) || vertices.length == 0) return -1;
        Direction facing = state.getValue(RoadBlock.FACING);
        double minX = 1, maxX = 0, minZ = 1, maxZ = 0, area = 0;
        for (int i = 0; i + 11 < vertices.length; i += 12) {
            for (int v = 0; v < 4; v++) {
                minX = Math.min(minX, vertices[i + v * 3]); maxX = Math.max(maxX, vertices[i + v * 3]);
                minZ = Math.min(minZ, vertices[i + v * 3 + 2]); maxZ = Math.max(maxZ, vertices[i + v * 3 + 2]);
            }
            for (int v = 1; v <= 2; v++) area += Math.abs((vertices[i + v*3] - vertices[i]) * (vertices[i + (v+1)*3 + 2] - vertices[i + 2])
                    - (vertices[i + v*3 + 2] - vertices[i + 2]) * (vertices[i + (v+1)*3] - vertices[i])) / 2;
        }
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        if (Math.abs((alongX ? minX : minZ)) > 1E-5 || Math.abs((alongX ? maxX : maxZ) - 1) > 1E-5) return -1;
        int style = state.getValue(ConnectedRoadBlock.STYLE).ordinal();
        if (Math.abs(area - (style == 0 ? .125 : style == 2 ? .5 : .25)) > 1E-5) return -1;
        double centre = alongX ? (minZ + maxZ) / 2 : (minX + maxX) / 2;
        for (int position = 0; position <= 2; position++)
            if (Math.abs(centre - .5 - RoadAutoGeometry.offset(facing, position)) < 1E-5) return position;
        return -1;
    }

    public static float[] topVertices(BlockGetter level, BlockPos pos, BlockState state, ColoredBlockEntity entity) {
        int position = automaticPosition(state, entity);
        if (position < 0) return entity.getStrokeVertices();
        double[] ports = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        if (level != null) for (Direction side : Direction.Plane.HORIZONTAL) {
            Surface neighbour = neighbour(level, pos, side, height(state));
            if (neighbour == null || !connectedRoad(neighbour.state)
                    || neighbour.state.getValue(ConnectedRoadBlock.STYLE) != state.getValue(ConnectedRoadBlock.STYLE)
                    || !(level.getBlockEntity(neighbour.pos) instanceof ColoredBlockEntity other)) continue;
            if (!other.getCustomRoadPattern().isEmpty()) continue;
            // Old saved straight meshes may also belong to a multi-block operation.
            // Keep their existing ports until an explicitly automatic neighbour is painted.
            if (entity.getAutomaticLinePosition() == -2 && other.getAutomaticLinePosition() < 0
                    && !Double.isFinite(edgeCentre(entity.getStrokeVertices(), side))) continue;
            if (other.getAutomaticLinePosition() == -2 && entity.getAutomaticLinePosition() < 0
                    && !Double.isFinite(edgeCentre(other.getStrokeVertices(), side.getOpposite()))) continue;
            int otherPosition = automaticPosition(neighbour.state, other);
            double local = .5 + RoadAutoGeometry.offset(state.getValue(RoadBlock.FACING), position);
            if (otherPosition >= 0) ports[side.get2DDataValue()] = (local + .5
                    + RoadAutoGeometry.offset(neighbour.state.getValue(RoadBlock.FACING), otherPosition)) / 2;
            else ports[side.get2DDataValue()] = edgeCentre(other.getStrokeVertices(), side.getOpposite());
        }
        float[] vertices = RoadAutoGeometry.connected(state.getValue(RoadBlock.FACING), state.getValue(ConnectedRoadBlock.STYLE).ordinal(), position, ports);
        float y = (float) height(state) + LIFT;
        for (int i = 1; i < vertices.length; i += 3) vertices[i] = y;
        return vertices;
    }

    private static double edgeCentre(float[] vertices, Direction side) {
        double min = 1, max = 0;
        boolean found = false;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            float boundary = side.getAxis() == Direction.Axis.X ? vertices[i] : vertices[i + 2];
            if (Math.abs(boundary - (side == Direction.EAST || side == Direction.SOUTH ? 1 : 0)) > 1E-5) continue;
            float coordinate = side.getAxis() == Direction.Axis.X ? vertices[i + 2] : vertices[i];
            min = Math.min(min, coordinate); max = Math.max(max, coordinate); found = true;
        }
        return found && max > min ? (min + max) / 2 : Double.NaN;
    }
    private static Surface neighbour(BlockGetter level, BlockPos pos, Direction side, double top) {
        Surface closest = null;
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos next = pos.relative(side).offset(0, dy, 0);
            BlockState state = level.getBlockState(next);
            if (!RoadPainting.isRoad(state) || RoadPainting.isRoad(level.getBlockState(next.above()))) continue;
            double y = dy + height(state);
            if (closest == null || Math.abs(y - top) < Math.abs(closest.height - top)) closest = new Surface(next, state, y);
        }
        return closest;
    }
    public static float[] renderVertices(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof ColoredBlockEntity entity)) return new float[0];
        float[] top = topVertices(level, pos, state, entity);
        double height = height(state);
        float[] bottoms = new float[4];
        Arrays.fill(bottoms, (float) height);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Surface neighbour = neighbour(level, pos, side, height);
            bottoms[side.get2DDataValue()] = neighbour != null ? (float) Math.max(0, neighbour.height)
                    : state.getBlock() instanceof ConnectedRoadSlopeBlock ? 0 : (float) height;
        }
        return shell(top, bottoms);
    }
    /** Extend paint over the exact top/side join; never leave the lift distance unpainted. */
    public static float[] shell(float[] top, float[] bottoms) {
        List<Float> mesh = new ArrayList<>();
        for (int q = 0; q + 11 < top.length; q += 12) {
            float[] cap = Arrays.copyOfRange(top, q, q + 12);
            for (int v = 0; v < 4; v++) {
                cap[v*3] = pad(cap[v*3]); cap[v*3 + 2] = pad(cap[v*3 + 2]);
            }
            add(mesh, cap);
            for (int v = 0; v < 4; v++) {
                int a = v*3, b = ((v+1)%4)*3;
                float ax = top[q+a], az = top[q+a+2], bx = top[q+b], bz = top[q+b+2];
                if (Math.abs(ax-bx) + Math.abs(az-bz) < 1E-6F) continue;
                Direction side = ax == 0 && bx == 0 ? Direction.WEST : ax == 1 && bx == 1 ? Direction.EAST
                        : az == 0 && bz == 0 ? Direction.NORTH : az == 1 && bz == 1 ? Direction.SOUTH : null;
                if (side == null || bottoms[side.get2DDataValue()] >= top[q+a+1] - LIFT - 1E-6F) continue;
                float bottom = Math.max(0, bottoms[side.get2DDataValue()]);
                add(mesh, new float[]{cap[a],cap[a+1],cap[a+2],cap[a],bottom,cap[a+2],
                        cap[b],bottom,cap[b+2],cap[b],cap[b+1],cap[b+2]});
            }
        }
        float[] vertices = new float[mesh.size()];
        for (int i = 0; i < vertices.length; i++) vertices[i] = mesh.get(i);
        return vertices;
    }
    private static float pad(float coordinate) {
        return coordinate == 0 ? -LIFT : coordinate == 1 ? 1 + LIFT : coordinate;
    }
    private static void add(List<Float> mesh, float[] quad) { for (float value : quad) mesh.add(value); }
}
