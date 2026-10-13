package com.destan.trafficengine.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
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

/** Static speed-bump geometry with no faces intruding into the supporting block. */
public final class GroundedSpeedBumpModel implements BakedModel {
    private final BakedModel original;
    private final List<BakedQuad> unculled;
    private final Map<Direction, List<BakedQuad>> sides = new EnumMap<>(Direction.class);

    public GroundedSpeedBumpModel(BakedModel original, BlockState state) {
        this.original = original;
        // These JSON models have no random variants. Clip once per resource reload,
        // never while drawing the world or rebuilding a chunk.
        unculled = clip(original.getQuads(state, null, RandomSource.create(0)));
        for (Direction side : Direction.values()) {
            sides.put(side, clip(original.getQuads(state, side, RandomSource.create(0))));
        }
    }

    private static List<BakedQuad> clip(List<BakedQuad> quads) {
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            for (int[] vertices : GroundPlaneQuadClipper.clip(quad.getVertices())) {
                result.add(vertices == quad.getVertices() ? quad : new BakedQuad(vertices,
                        quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
            }
        }
        return List.copyOf(result);
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? unculled : sides.get(side);
    }
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
}
