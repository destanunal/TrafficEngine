package com.destan.trafficengine.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the original asphalt's position-dependent texture beneath every marking. */
public final class PreservedAsphaltModel implements BakedModel {
    public record CacheKey(BakedModel markings, BlockState baseState, boolean paintedBase, boolean fullPaint) {}
    private final BakedModel markings;
    private final BakedModel base;
    private final BlockState baseState;
    private final TextureAtlasSprite paintedSprite;
    private final boolean paintedBase;
    private final boolean fullPaint;
    private final Map<BakedQuad, BakedQuad> paintedQuads;

    public PreservedAsphaltModel(BakedModel markings, BakedModel base, BlockState baseState,
            TextureAtlasSprite paintedSprite, boolean paintedBase, boolean fullPaint,
            Map<BakedQuad, BakedQuad> paintedQuads) {
        this.markings = markings;
        this.base = base;
        this.baseState = baseState;
        this.paintedSprite = paintedSprite;
        this.paintedBase = paintedBase;
        this.fullPaint = fullPaint;
        this.paintedQuads = paintedQuads;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        // Sample the unpainted model first, using the renderer's original position seed.
        // Multipart backgrounds otherwise reset that seed and choose a different rotation.
        List<BakedQuad> original = base.getQuads(baseState, side, random);
        List<BakedQuad> result = new ArrayList<>(original.size() + 1);
        for (BakedQuad quad : original) {
            result.add(paintedBase ? paintedQuads.computeIfAbsent(quad, this::paintQuad) : quad);
        }
        if (!fullPaint) {
            for (BakedQuad quad : markings.getQuads(state, side, random)) {
                if (quad.getTintIndex() == 1) result.add(quad);
            }
        }
        return result;
    }

    private BakedQuad paintQuad(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        TextureAtlasSprite source = quad.getSprite();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            // Retain UV rotation and shrink while moving to the tintable asphalt sprite.
            float relativeU = (u - source.getU0()) / (source.getU1() - source.getU0());
            float relativeV = (v - source.getV0()) / (source.getV1() - source.getV0());
            vertices[offset + 4] = Float.floatToRawIntBits(paintedSprite.getU0()
                    + relativeU * (paintedSprite.getU1() - paintedSprite.getU0()));
            vertices[offset + 5] = Float.floatToRawIntBits(paintedSprite.getV0()
                    + relativeV * (paintedSprite.getV1() - paintedSprite.getV0()));
        }
        return new BakedQuad(vertices, 0, quad.getDirection(), paintedSprite, quad.isShade());
    }

    @Override public boolean useAmbientOcclusion() { return markings.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return markings.isGui3d(); }
    @Override public boolean usesBlockLight() { return markings.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return markings.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return markings.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return markings.getOverrides(); }
}
