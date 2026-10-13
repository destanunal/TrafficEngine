package com.destan.trafficengine.block.data;

import java.util.List;

/** A single enclosing rectangle for either straight or diagonal placement. */
public final class RoundedSeparatorHitbox {
    private static final double HEIGHT = 4.25;
    private static final double EPSILON = 1.0e-6;

    private RoundedSeparatorHitbox() {}

    public static List<double[]> boxes(double clockwiseDegrees) {
        double angle = Math.toRadians(clockwiseDegrees);
        // The shell has a six-unit-wide capsule footprint with an eight-unit
        // straight centre. Bound the placed footprint without adding ramp tiers.
        double halfX = 3 + 4 * Math.abs(Math.sin(angle));
        double halfZ = 3 + 4 * Math.abs(Math.cos(angle));
        return List.of(new double[] {8 - halfX - EPSILON, 0, 8 - halfZ - EPSILON,
            8 + halfX + EPSILON, HEIGHT, 8 + halfZ + EPSILON});
    }
}
