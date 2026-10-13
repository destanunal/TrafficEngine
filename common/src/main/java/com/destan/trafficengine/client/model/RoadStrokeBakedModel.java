package com.destan.trafficengine.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Stroke quads live in the chunk mesh; there is no per-frame block entity renderer. */
public abstract class RoadStrokeBakedModel implements BakedModel {
    protected final BakedModel original;
    private final TextureAtlasSprite white;
    private final Map<float[],List<BakedQuad>> meshes=new WeakHashMap<>();
    protected RoadStrokeBakedModel(BakedModel original,TextureAtlasSprite white) {this.original=original;this.white=white;}
    protected List<BakedQuad> decorate(List<BakedQuad> base, BlockState state, Direction side, float[] vertices) {
        if (vertices == null || vertices.length == 0) return base;
        List<BakedQuad> result = new ArrayList<>(base.size() + vertices.length / 12);
        for (BakedQuad quad : base) if (quad.getTintIndex() != 1) result.add(quad);
        // Partial-height tops and their risers belong to the unculled pass.
        // Returning them here also covers renderers which request general quads.
        if (side == null) {
            synchronized (meshes) { result.addAll(meshes.computeIfAbsent(vertices, this::bake)); }
        }
        return result;
    }
    public List<BakedQuad> bake(float[] vertices, boolean slope) {
        return bake(slope ? com.destan.trafficengine.item.RoadMarkingMesh.shell(vertices,new float[4]) : vertices);
    }
    public List<BakedQuad> bake(float[] vertices) {
        List<BakedQuad> result = new ArrayList<>();
        for (int q=0;q+11<vertices.length;q+=12) {
            float[] face=java.util.Arrays.copyOfRange(vertices,q,q+12);
            float ax=face[3]-face[0],ay=face[4]-face[1],az=face[5]-face[2];
            float bx=face[6]-face[0],by=face[7]-face[1],bz=face[8]-face[2];
            float cx=face[9]-face[0],cy=face[10]-face[1],cz=face[11]-face[2];
            result.add(quad(face,Direction.getNearest(ay*bz-az*by+by*cz-bz*cy,
                    az*bx-ax*bz+bz*cx-bx*cz,ax*by-ay*bx+bx*cy-by*cx)));
        }
        return List.copyOf(result);
    }
    private BakedQuad quad(float[] vertices, Direction face) {
        int[] data = new int[32];
        for (int v = 0; v < 4; v++) {
            int out = v * 8, in = v * 3;
            float x = vertices[in], y = vertices[in + 1], z = vertices[in + 2];
            data[out] = Float.floatToRawIntBits(x); data[out+1] = Float.floatToRawIntBits(y); data[out+2] = Float.floatToRawIntBits(z);
            data[out+3] = -1;
            float u = face.getAxis() == Direction.Axis.X ? z : x;
            float textureV = face == Direction.UP ? z : y;
            float shrink = white.uvShrinkRatio();
            u = 0.5F + (Math.max(0, Math.min(1, u)) - 0.5F) * (1 - shrink);
            textureV = 0.5F + (Math.max(0, Math.min(1, textureV)) - 0.5F) * (1 - shrink);
            data[out+4] = Float.floatToRawIntBits(white.getU(u*16));
            data[out+5] = Float.floatToRawIntBits(white.getV(textureV*16));
            data[out+7] = (face.getStepX() * 127 & 255) | (face.getStepY() * 127 & 255) << 8 | (face.getStepZ() * 127 & 255) << 16;
        }
        return new BakedQuad(data, 1, face, white, true);
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) {return original.getQuads(state,side,random);}
    @Override public boolean useAmbientOcclusion() {return original.useAmbientOcclusion();}
    @Override public boolean isGui3d() {return original.isGui3d();}
    @Override public boolean usesBlockLight() {return original.usesBlockLight();}
    @Override public boolean isCustomRenderer() {return false;}
    @Override public TextureAtlasSprite getParticleIcon() {return original.getParticleIcon();}
    @Override public ItemTransforms getTransforms() {return original.getTransforms();}
    @Override public ItemOverrides getOverrides() {return original.getOverrides();}
}
