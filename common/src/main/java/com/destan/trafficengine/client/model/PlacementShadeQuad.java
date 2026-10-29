package com.destan.trafficengine.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;

/** Baked shade reference: rigid placements use a direction; curved faces blend continuously. */
public final class PlacementShadeQuad extends BakedQuad {
    private final Direction shadeDirection;
    private final float xWeight,yWeight,zWeight,vertical;
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
    public Direction getShadeDirection(){return shadeDirection;}
    public float shade(BlockAndTintGetter level) {
        if(shadeDirection!=null)return level.getShade(shadeDirection,isShade());
        float sideX=xWeight==0?0:level.getShade(Direction.EAST,isShade())*xWeight;
        float sideZ=zWeight==0?0:level.getShade(Direction.NORTH,isShade())*zWeight;
        float up=level.getShade(Direction.UP,isShade()),down=level.getShade(Direction.DOWN,isShade());
        return Math.min(1,sideX+sideZ+yWeight*((up+down)*.5f+vertical*(up-down)*.5f));
    }
}
