package com.destan.trafficengine.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;

/** Baked shade reference: rigid placements use a direction; curved faces blend continuously. */
public final class PlacementShadeQuad extends BakedQuad {
    private final Direction shadeDirection;
    private final float xWeight,yWeight,zWeight,vertical;
    private float[] vertexWeights; // Only curved shells; allocated once during resource baking.
    public PlacementShadeQuad(BakedQuad original,Direction shadeDirection) {
        this(original,shadeDirection,0,0,0,0);
    }
    private PlacementShadeQuad(BakedQuad original,Direction direction,float x,float y,float z,float vertical) {
        super(original.getVertices(),original.getTintIndex(),original.getDirection(),original.getSprite(),original.isShade());
        shadeDirection=direction;xWeight=x;yWeight=y;zWeight=z;this.vertical=vertical;
    }
    public static PlacementShadeQuad smooth(BakedQuad original) {
        int n=original.getVertices()[7];float x=(byte)n,y=(byte)(n>>8),z=(byte)(n>>16);
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        if(length==0)return new PlacementShadeQuad(original,original.getDirection());
        x/=length;y/=length;z/=length;
        return new PlacementShadeQuad(original,null,x*x,y*y,z*z,y);
    }
    public static PlacementShadeQuad smooth(BakedQuad original,float[] normals) {
        PlacementShadeQuad result=smooth(original);
        if(normals!=null) {
            result.vertexWeights=new float[16];
            for(int i=0;i<4;i++) {
                float x=normals[i*3],y=normals[i*3+1],z=normals[i*3+2];
                float inverse=1f/(float)Math.sqrt(x*x+y*y+z*z);
                x*=inverse;y*=inverse;z*=inverse;
                result.vertexWeights[i*4]=x*x;result.vertexWeights[i*4+1]=y*y;
                result.vertexWeights[i*4+2]=z*z;result.vertexWeights[i*4+3]=y;
            }
        }
        return result;
    }
    public boolean hasVertexShade(){return vertexWeights!=null;}
    public float shade(BlockAndTintGetter level,int vertex) {
        if(vertexWeights==null)return shade(level);
        int offset=vertex*4;
        float up=level.getShade(Direction.UP,isShade()),down=level.getShade(Direction.DOWN,isShade());
        return vertexShade(offset,level.getShade(Direction.EAST,isShade()),level.getShade(Direction.NORTH,isShade()),up,down);
    }
    private float vertexShade(int offset,float x,float z,float up,float down) {
        return Math.min(1,vertexWeights[offset]*x+vertexWeights[offset+2]*z
                +vertexWeights[offset+1]*((up+down)*.5f+vertexWeights[offset+3]*(up-down)*.5f));
    }
    /** Reuse the loader's four-value buffer; preserve AO, lightmaps and coloured light. */
    public void applyVertexShade(BlockAndTintGetter level,float[] brightness) {
        if(vertexWeights==null)return;
        float face=shade(level);
        if(face<=0)return;
        float x=level.getShade(Direction.EAST,isShade()),z=level.getShade(Direction.NORTH,isShade());
        float up=level.getShade(Direction.UP,isShade()),down=level.getShade(Direction.DOWN,isShade());
        for(int i=0;i<4;i++)brightness[i]*=vertexShade(i*4,x,z,up,down)/face;
    }
    public Direction getShadeDirection(){return shadeDirection;}
    public float shade(BlockAndTintGetter level) {
        if(shadeDirection!=null)return level.getShade(shadeDirection,isShade());
        float sideX=xWeight==0?0:level.getShade(Direction.EAST,isShade())*xWeight;
        float sideZ=zWeight==0?0:level.getShade(Direction.NORTH,isShade())*zWeight;
        float up=level.getShade(Direction.UP,isShade()),down=level.getShade(Direction.DOWN,isShade());
        return Math.min(1,sideX+sideZ+yWeight*((up+down)*.5f+vertical*(up-down)*.5f));
    }
}
