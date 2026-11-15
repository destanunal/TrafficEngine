package com.destan.trafficengine.client.model;

import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Connected casings stay in the chunk mesh, with four bounded variants per baked model. */
public abstract class RoadSignJoinedBakedModel implements BakedModel {
    public record CacheKey(BakedModel model, Kind kind, Direction facing, boolean mounted, boolean wall) {}
    public static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH,
        Direction.SOUTH, Direction.WEST, Direction.EAST};
    protected final BakedModel original;
    // One current illumination per join variant: bounded, atlas-lifetime geometry only.
    private final int[] cachedLight={-1,-1,-1,-1};
    @SuppressWarnings("unchecked") private final List<BakedQuad>[][] litCache=new List[4][7];
    private final BlockState prototype;
    @SuppressWarnings("unchecked") private final List<BakedQuad>[][] cache = new List[4][7];

    protected RoadSignJoinedBakedModel(BakedModel original, BlockState state) {
        this.original = original;
        this.prototype = state;
    }
    public static CacheKey key(BakedModel model, BlockState state) {
        return new CacheKey(model, ((RoadInformationSignBlock)state.getBlock()).kind(),
            state.getValue(RoadInformationSignBlock.FACING), state.getValue(RoadInformationSignBlock.POST_MOUNTED),
            state.getValue(RoadInformationSignBlock.WALL_MOUNTED));
    }
    public static int connections(BlockGetter level, BlockPos pos) {
        // Chunk builders supply their immutable render region; never scan a live world from a worker.
        return level.getBlockEntity(pos) instanceof RoadInformationSignBlockEntity sign
            ? sign.findHorizontalConnections(level) : 0;
    }
    protected List<BakedQuad> joined(Direction side, int joins) {
        int index=side==null?0:side.ordinal()+1;
        synchronized(cache) {
            List<BakedQuad> value=cache[joins&3][index];
            if(value==null) {
                List<BakedQuad> source=original.getQuads(prototype,side,RandomSource.create(0));
                if(joins==0)value=source;
                else {
                    List<BakedQuad> result=new ArrayList<>(source.size());
                    for(BakedQuad quad:source) {
                        for(int[] vertices:connectedVertices(quad.getVertices(),prototype,joins))
                            result.add(vertices==quad.getVertices()?quad:quad instanceof PlacementShadeQuad matched
                                ?matched.withVertices(vertices):new BakedQuad(vertices,
                                    quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade()));
                    }
                    value=List.copyOf(result);
                }
                cache[joins&3][index]=value;
            }
            return value;
        }
    }

    public static int surfaceLight(net.minecraft.world.level.BlockAndTintGetter level,BlockPos pos,BlockState state) {
        BlockPos sample=com.destan.trafficengine.block.data.RoadSignLightPosition.sample(pos,state);
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level,sample);
    }
    /** Keep world illumination, but never let a rear wall's zero light blacken the plate. */
    public static BakedQuad surfaceLit(BakedQuad quad,int light) {
        if(quad.getTintIndex()!=110)return quad;
        int[] data=quad.getVertices().clone();int stride=data.length/4;
        for(int i=0;i<4;i++)data[i*stride+6]=light;
        return new BakedQuad(data,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade());
    }
    protected List<BakedQuad> lit(Direction side,int joins,int light) {
        int mask=joins&3,index=side==null?0:side.ordinal()+1;
        synchronized(cache) {
            if(cachedLight[mask]!=light) {
                java.util.Arrays.fill(litCache[mask],null);cachedLight[mask]=light;
            }
            var value=litCache[mask][index];
            if(value==null) {
                var source=joined(side,mask);var result=new ArrayList<BakedQuad>(source.size());
                for(var quad:source)result.add(surfaceLit(quad,light));
                value=List.copyOf(result);litCache[mask][index]=value;
            }
            return value;
        }
    }

    /** Tile the tiny rail extensions instead of stretching the original sixteen-texel pole grain. */
    public static List<int[]> connectedVertices(int[] source,BlockState state,int joins) {
        int[] joined=joinVertices(source,state,joins);
        if(joined==null)return List.of();
        if(joined==source)return List.of(source);
        Direction facing=state.getValue(RoadInformationSignBlock.FACING);
        Kind kind=((RoadInformationSignBlock)state.getBlock()).kind();
        double left=kind==Kind.LARGE?-24:-8,right=kind==Kind.LARGE?40:24;
        int stride=source.length/4;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int v=0;v<4;v++){double x=canonicalX(source,v*stride,facing);min=Math.min(min,x);max=Math.max(max,x);}
        boolean extendLeft=(joins&2)!=0&&Math.abs(min-left-3)<1E-4;
        boolean extendRight=(joins&1)!=0&&Math.abs(max-right+3)<1E-4;
        if((!extendLeft&&!extendRight)||max-min<3)return List.of(joined);
        List<int[]> pieces=new ArrayList<>(3);pieces.add(source);
        for(boolean lower:new boolean[]{true,false}) {
            if(lower?!extendLeft:!extendRight)continue;
            int[] extension=source.clone();
            for(int v=0;v<4;v++) {
                int p=v*stride;double x=canonicalX(source,p,facing);
                boolean atMinimum=Math.abs(x-min)<1E-4;
                double target=lower?(atMinimum?min-3:min):(atMinimum?max:max+3);
                // Sample a three-unit strip from this same face; its UV density is unchanged.
                double sample=lower?(atMinimum?min:min+3):(atMinimum?max-3:max);
                int opposite=-1;
                for(int k=0;k<4;k++)if(Math.abs(canonicalX(source,k*stride,facing)-x)>1E-4
                    &&Math.abs(Float.intBitsToFloat(source[k*stride+1])-Float.intBitsToFloat(source[p+1]))<1E-5
                    &&Math.abs(Float.intBitsToFloat(source[k*stride+(facing.getAxis()==Direction.Axis.Z?2:0)])
                        -Float.intBitsToFloat(source[p+(facing.getAxis()==Direction.Axis.Z?2:0)]))<1E-5){opposite=k*stride;break;}
                if(opposite<0)return List.of(joined); // Preserve custom resource-pack geometry without failing a chunk build.
                double other=canonicalX(source,opposite,facing),t=(sample-x)/(other-x);
                for(int uv=4;uv<=5;uv++) {
                    float a=Float.intBitsToFloat(source[p+uv]),b=Float.intBitsToFloat(source[opposite+uv]);
                    extension[p+uv]=Float.floatToRawIntBits((float)(a+(b-a)*t));
                }
                int axis=facing.getAxis()==Direction.Axis.Z?0:2;
                double coordinate=facing==Direction.SOUTH||facing==Direction.WEST?16-target:target;
                extension[p+axis]=Float.floatToRawIntBits((float)(coordinate/16));
            }
            pieces.add(extension);
        }
        return pieces;
    }
    private static double canonicalX(int[] vertices,int offset,Direction facing) {
        double x=Float.intBitsToFloat(vertices[offset])*16,z=Float.intBitsToFloat(vertices[offset+2])*16;
        return switch(facing){case EAST->z;case SOUTH->16-x;case WEST->16-z;default->x;};
    }

    /** Extend only touching corner bands and rear rails; remove newly internal end faces. */
    public static int[] joinVertices(int[] source, BlockState state, int joins) {
        if(joins==0 || state.getValue(RoadInformationSignBlock.DIAGONAL))return source;
        Kind kind=((RoadInformationSignBlock)state.getBlock()).kind();
        if(kind==Kind.VARIABLE_SPEED)return source;
        Direction facing=state.getValue(RoadInformationSignBlock.FACING);
        double left=kind==Kind.LARGE?-24:-8, right=kind==Kind.LARGE?40:24;
        double front=RoadInformationSignBlock.frontDepth(kind,state.getValue(RoadInformationSignBlock.POST_MOUNTED),
            state.getValue(RoadInformationSignBlock.WALL_MOUNTED)),back=front+.5;
        int stride=source.length/4;
        double[] x=new double[4],z=new double[4];
        double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(int v=0;v<4;v++) {
            int p=v*stride;
            double wx=Float.intBitsToFloat(source[p])*16, wz=Float.intBitsToFloat(source[p+2])*16;
            x[v]=switch(facing){case EAST->wz;case SOUTH->16-wx;case WEST->16-wz;default->wx;};
            z[v]=switch(facing){case EAST->16-wx;case SOUTH->16-wz;case WEST->wx;default->wz;};
            double y=Float.intBitsToFloat(source[p+1])*16;
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            minZ=Math.min(minZ,z[v]);maxZ=Math.max(maxZ,z[v]);
        }
        boolean plate=minZ>=front-1E-4&&maxZ<=back+1E-4;
        boolean rail=!state.getValue(RoadInformationSignBlock.WALL_MOUNTED)
            &&minZ>=back-1E-4&&maxZ<=back+.5001&&maxY-minY<=1.0001;
        if(!plate&&!rail)return source;
        int[] result=null;
        for(int v=0;v<4;v++) {
            double target=x[v];
            // The front view's left is the canonical model's positive X edge.
            if((joins&RoadInformationSignBlockEntity.JOIN_RIGHT)!=0
                &&((plate&&x[v]<left+.6671)|| (rail&&Math.abs(x[v]-(left+3))<1E-4)))target=left;
            if((joins&RoadInformationSignBlockEntity.JOIN_LEFT)!=0
                &&((plate&&x[v]>right-.6671)|| (rail&&Math.abs(x[v]-(right-3))<1E-4)))target=right;
            if(Math.abs(target-x[v])<1E-5)continue;
            if(result==null)result=source.clone();
            x[v]=target;
            double wx=switch(facing){case EAST->16-z[v];case SOUTH->16-target;case WEST->z[v];default->target;};
            double wz=switch(facing){case EAST->target;case SOUTH->16-z[v];case WEST->16-target;default->z[v];};
            result[v*stride]=Float.floatToRawIntBits((float)(wx/16));
            result[v*stride+2]=Float.floatToRawIntBits((float)(wz/16));
        }
        boolean onLeft=true,onRight=true;
        for(double value:x){onLeft&=Math.abs(value-left)<1E-4;onRight&=Math.abs(value-right)<1E-4;}
        if((onLeft&&(joins&RoadInformationSignBlockEntity.JOIN_RIGHT)!=0)
            ||(onRight&&(joins&RoadInformationSignBlockEntity.JOIN_LEFT)!=0))return null;
        return result==null?source:result;
    }

    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) {return original.getQuads(state,side,random);}
    @Override public boolean useAmbientOcclusion() {return original.useAmbientOcclusion();}
    @Override public boolean isGui3d() {return original.isGui3d();}
    @Override public boolean usesBlockLight() {return original.usesBlockLight();}
    @Override public boolean isCustomRenderer() {return original.isCustomRenderer();}
    @Override public TextureAtlasSprite getParticleIcon() {return original.getParticleIcon();}
    @Override public ItemTransforms getTransforms() {return original.getTransforms();}
    @Override public ItemOverrides getOverrides() {return original.getOverrides();}
}
