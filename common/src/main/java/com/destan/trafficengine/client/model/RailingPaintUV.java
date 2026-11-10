package com.destan.trafficengine.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;

/** Solid paint needs no edge UVs. Applied once during model baking, never during rendering. */
public final class RailingPaintUV {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("trafficengine", "block/railing_paint");
    private RailingPaintUV() {}

    public static void prepare(BakedQuad quad) {
        var sprite = quad.getSprite();
        if (!TEXTURE.equals(sprite.contents().name())) return;
        // Thin caps inherited UV spans smaller than Sodium's packed UV precision.
        // Sample the interior of the shared 32px solid sprite instead of the atlas edge.
        center(quad.getVertices(), (sprite.getU0() + sprite.getU1()) * .5F,
                (sprite.getV0() + sprite.getV1()) * .5F);
    }

    static void center(int[] vertices, float u, float v) {
        int stride = vertices.length / 4;
        int packedU = Float.floatToRawIntBits(u), packedV = Float.floatToRawIntBits(v);
        for (int i = 0; i < 4; i++) {
            vertices[i * stride + 4] = packedU;
            vertices[i * stride + 5] = packedV;
        }
    }
}
