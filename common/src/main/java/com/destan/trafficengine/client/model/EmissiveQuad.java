package com.destan.trafficengine.client.model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
public final class EmissiveQuad extends BakedQuad {
    public static final float[] BRIGHTNESS = {1,1,1,1};
    public static final int[] LIGHT = {LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT};
    public EmissiveQuad(BakedQuad original) {
        super(original.getVertices(),original.getTintIndex(),original.getDirection(),original.getSprite(),false);
        int[] vertices = getVertices();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) vertices[vertex * stride + 6] = LightTexture.FULL_BRIGHT;
    }
    // Forge checks this additional method before applying ambient shading.
    public boolean hasAmbientOcclusion() {return false;}
}
