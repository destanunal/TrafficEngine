package com.destan.trafficengine.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Retains geometric facing while matching the shade of the straight placement. */
public final class PlacementShadeQuad extends BakedQuad {
    private final Direction shadeDirection;

    public PlacementShadeQuad(BakedQuad original, Direction shadeDirection) {
        super(original.getVertices(), original.getTintIndex(), original.getDirection(),
            original.getSprite(), original.isShade());
        this.shadeDirection = shadeDirection;
    }

    public Direction getShadeDirection() { return shadeDirection; }
}
