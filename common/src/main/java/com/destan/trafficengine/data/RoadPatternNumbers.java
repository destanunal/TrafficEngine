package com.destan.trafficengine.data;

import com.destan.trafficengine.Constants;

/** Contiguous menu numbers without changing persistent road-block or brush IDs. */
public final class RoadPatternNumbers {
    private RoadPatternNumbers() {}

    public static boolean isVisible(int id) {
        return id >= 0 && id < Constants.MAX_ASPHALT_PATTERNS && id != 29 && id != 30;
    }

    /** Retired forms still load, but share a surviving form's caption and favorite. */
    public static int canonicalId(int id) {
        return id == 29 ? 11 : id == 30 ? 10 : id;
    }

    public static int displayNumber(int id) {
        id = canonicalId(id);
        return id > 30 && id < Constants.MAX_ASPHALT_PATTERNS ? id - 2 : id;
    }
}
