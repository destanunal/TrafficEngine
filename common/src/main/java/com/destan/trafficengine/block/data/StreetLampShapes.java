package com.destan.trafficengine.block.data;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Simple cached outlines fitted to the enlarged street lamp models. */
public final class StreetLampShapes {
    private StreetLampShapes() {
    }

    // One box for the stem and one for the complete upper assembly.
    // Keep curved arms inside a single box to avoid dense selection outlines.
    public static final Map<Direction, VoxelShape> NORMAL = oriented(Shapes.or(
        Block.box(7, 0, 7, 9, 4, 9),
        Block.box(4.64, 3.26, -11, 11.36, 10.3, 9.01)
    ));

    public static final Map<Direction, VoxelShape> DOUBLE = oriented(Shapes.or(
        Block.box(7, 0, 7, 9, 4, 9),
        Block.box(4.64, 3.26, -11, 11.36, 10.3, 27)
    ));

    public static final Map<Direction, VoxelShape> SMALL = oriented(Shapes.or(
        Block.box(7, 0, 7, 9, 12.24, 9),
        Block.box(4.64, 12.24, -3.75, 11.36, 15.3, 12.75)
    ));

    public static final Map<Direction, VoxelShape> SMALL_DOUBLE = oriented(Shapes.or(
        Block.box(7, 0, 7, 9, 12.24, 9),
        Block.box(4.64, 12.24, -5.5, 11.36, 15.3, 21.5)
    ));

    public static final Map<Direction, VoxelShape> STREET_LIGHT = oriented(
        Block.box(2.96, 5.74, 0.5, 13.04, 9.3, 15.5)
    );

    public static final Map<Direction, VoxelShape> FLUORESCENT = oriented(
        Block.box(4.64, 5.99, -4, 11.36, 9.3, 20)
    );

    private static Map<Direction, VoxelShape> oriented(VoxelShape north) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, north.optimize());
        VoxelShape rotated = north;
        for (Direction direction : new Direction[] {Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape result = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                result = Shapes.or(result, Shapes.box(
                    1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX));
            }
            rotated = result.optimize();
            shapes.put(direction, rotated);
        }
        return Map.copyOf(shapes);
    }
}
