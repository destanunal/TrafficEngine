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
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(int i=0;i+2<vertices.length;i+=3) {
            minX=Math.min(minX,vertices[i]);maxX=Math.max(maxX,vertices[i]);
            minZ=Math.min(minZ,vertices[i+2]);maxZ=Math.max(maxZ,vertices[i+2]);
        }
        // Short dashed pieces must retain the same grain orientation as the whole stroke.
        boolean alongX=maxX-minX>maxZ-minZ;
        boolean straight=Math.min(maxX-minX,maxZ-minZ)<=.6251F;
        for (int q=0;q+11<vertices.length;q+=12) {
            float[] face=java.util.Arrays.copyOfRange(vertices,q,q+12);
            // Older saves contain two triangles for each rectangle. Join them at bake time,
            // retaining the stored footprint while avoiding a diagonal lighting seam.
            if(q+23<vertices.length && legacyRectangle(vertices,q)) {
                System.arraycopy(vertices,q+18,face,9,3);q+=12;
            }
            float[] ordered=face;
            int first=0;
            for(int i=1;i<4;i++)if(face[i*3]+face[i*3+2]<face[first*3]+face[first*3+2])first=i;
            if(first!=0) {
                ordered=new float[12];
                for(int i=0;i<4;i++)System.arraycopy(face,((first+i)&3)*3,ordered,i*3,3);
            }
            face=ordered;
            float ax=face[3]-face[0],ay=face[4]-face[1],az=face[5]-face[2];
            float bx=face[6]-face[0],by=face[7]-face[1],bz=face[8]-face[2];
            float cx=face[9]-face[0],cy=face[10]-face[1],cz=face[11]-face[2];
            result.add(quad(face,Direction.getNearest(ay*bz-az*by+by*cz-bz*cy,
                    az*bx-ax*bz+bz*cx-bx*cz,ax*by-ay*bx+bx*cy-by*cx),straight?alongX:null));
        }
        return List.copyOf(result);
    }
    private static boolean same(float[] mesh,int a,int b) {
        return mesh[a]==mesh[b]&&mesh[a+1]==mesh[b+1]&&mesh[a+2]==mesh[b+2];
    }
    private static boolean legacyRectangle(float[] mesh,int q) {
        if(!same(mesh,q+6,q+9)||!same(mesh,q+18,q+21)||!same(mesh,q,q+12)||!same(mesh,q+6,q+15))return false;
        int[] corners={q,q+3,q+6,q+18};
        for(int i=0;i<4;i++) {
            int a=corners[i],b=corners[(i+1)&3],c=corners[(i+2)&3];
            if(mesh[a+1]!=mesh[q+1])return false;
            double ax=mesh[b]-mesh[a],az=mesh[b+2]-mesh[a+2],bx=mesh[c]-mesh[b],bz=mesh[c+2]-mesh[b+2];
            if(ax*ax+az*az<1E-10||Math.abs(ax*bx+az*bz)>1E-6)return false;
        }
        return true;
    }
    private BakedQuad quad(float[] vertices, Direction face,Boolean strokeAlongX) {
        int[] data = new int[32];
        // Sample the original manual sprite at one texel per model unit.
        // Sharing its atlas entry also shares mipmaps and resource-pack replacements.
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
        float minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(int i=0;i<12;i+=3) {
            minX=Math.min(minX,vertices[i]);maxX=Math.max(maxX,vertices[i]);
            minZ=Math.min(minZ,vertices[i+2]);maxZ=Math.max(maxZ,vertices[i+2]);
        }
        boolean alongX=strokeAlongX!=null?strokeAlongX:maxX-minX>maxZ-minZ;
        for (int v = 0; v < 4; v++) {
            int out = v * 8, in = v * 3;
            float x = vertices[in], y = vertices[in + 1], z = vertices[in + 2];
            data[out] = Float.floatToRawIntBits(x); data[out+1] = Float.floatToRawIntBits(y); data[out+2] = Float.floatToRawIntBits(z);
            data[out+3] = -1;
            float across=alongX?z:x, minimum=alongX?minZ:minX, span=alongX?maxZ-minZ:maxX-minX;
            float u=.5F + across - (minimum + span * .5F);
            // Oblique curve pieces may have a wider bounding box than the paint strip.
            // Keep their silhouettes intact without sampling the sprite's transparent background.
            u=Math.max(.375F,Math.min(.625F,u));
            float textureV=face==Direction.UP?(alongX?x:z):y;
            float shrink = white.uvShrinkRatio();
            u = 0.5F + (Math.max(0, Math.min(1, u)) - 0.5F) * (1 - shrink);
            textureV = 0.5F + (Math.max(0, Math.min(1, textureV)) - 0.5F) * (1 - shrink);
            data[out+4] = Float.floatToRawIntBits(white.getU(u*1));
            data[out+5] = Float.floatToRawIntBits(white.getV(textureV*1));
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
