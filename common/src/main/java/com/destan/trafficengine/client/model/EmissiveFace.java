package com.destan.trafficengine.client.model;
public interface EmissiveFace {
    /** Optional explicit quad corners, in model units, for curved block shells. */
    float[] trafficengine$getVertices();
    void trafficengine$setVertices(float[] vertices);
    boolean trafficengine$matchesStraightShade();
    void trafficengine$setMatchesStraightShade(boolean value);
    boolean trafficengine$isEmissive();
    void trafficengine$setEmissive(boolean value);
}
