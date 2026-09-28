package com.destan.trafficengine.block.data;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DiagonalVoxelShapes {

    private static final double COS_45 = Math.sqrt(0.5d);
    private static final double SIN_45 = COS_45;
    private static final double MAX_SEGMENT_LENGTH = 2.0d / 16.0d;

    private DiagonalVoxelShapes() {
    }

    public static VoxelShape rotateClockwise45(VoxelShape source) {
        return rotateClockwise45(source, 0.5d, 0.5d);
    }

    public static VoxelShape rotateClockwise45(VoxelShape source, double pivotX, double pivotZ) {
        if (source.isEmpty()) {
            return Shapes.empty();
        }

        VoxelShape result = Shapes.empty();

        for (AABB box : source.toAabbs()) {
            double sizeX = box.maxX - box.minX;
            double sizeZ = box.maxZ - box.minZ;
            boolean splitX = sizeX >= sizeZ;
            double majorSize = splitX ? sizeX : sizeZ;
            int segments = Math.max(1, (int)Math.ceil(majorSize / MAX_SEGMENT_LENGTH));

            for (int i = 0; i < segments; i++) {
                double start = (double)i / segments;
                double end = (double)(i + 1) / segments;

                double minX = splitX ? box.minX + sizeX * start : box.minX;
                double maxX = splitX ? box.minX + sizeX * end : box.maxX;
                double minZ = splitX ? box.minZ : box.minZ + sizeZ * start;
                double maxZ = splitX ? box.maxZ : box.minZ + sizeZ * end;

                double[] bounds = rotatedBounds(minX, minZ, maxX, maxZ, pivotX, pivotZ);
                result = Shapes.or(result, Shapes.box(
                    bounds[0], box.minY, bounds[1],
                    bounds[2], box.maxY, bounds[3]
                ));
            }
        }
        return result.optimize();
    }

    private static double[] rotatedBounds(
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            double pivotX,
            double pivotZ
    ) {
        double resultMinX = Double.POSITIVE_INFINITY;
        double resultMinZ = Double.POSITIVE_INFINITY;
        double resultMaxX = Double.NEGATIVE_INFINITY;
        double resultMaxZ = Double.NEGATIVE_INFINITY;

        double[] xs = {minX, maxX};
        double[] zs = {minZ, maxZ};

        for (double x : xs) {
            for (double z : zs) {
                double dx = x - pivotX;
                double dz = z - pivotZ;

                double rotatedX =
                        pivotX + dx * COS_45 - dz * SIN_45;

                double rotatedZ =
                        pivotZ + dx * SIN_45 + dz * COS_45;

                resultMinX = Math.min(resultMinX, rotatedX);
                resultMinZ = Math.min(resultMinZ, rotatedZ);
                resultMaxX = Math.max(resultMaxX, rotatedX);
                resultMaxZ = Math.max(resultMaxZ, rotatedZ);
            }
        }

        return new double[] {
                resultMinX,
                resultMinZ,
                resultMaxX,
                resultMaxZ
        };
    }
}
