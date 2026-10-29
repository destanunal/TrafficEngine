package com.destan.trafficengine.block.data;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Bounded, lazily cached model edges. Picking/collision retain their precise voxel shapes. */
public final class TrafficSignOutlineGeometry {
    private static final float[][][][] PLATES=new float[TrafficSignShape.values().length][4][3][];
    private static final float[][][] POLES=new float[TrafficSignShape.values().length][4][];
    private static final double SIN=Math.sin(Math.PI/8), COS=Math.cos(Math.PI/8);
    private TrafficSignOutlineGeometry() {}
    public static float[] plate(TrafficSignShape shape,Direction facing,boolean tilted) {
        return plate(shape,facing,tilted,true);
    }
    public static float[] plate(TrafficSignShape shape,Direction facing,boolean tilted,boolean diagonal) {
        int kind=shape.ordinal(), dir=facing.get2DDataValue(), mode=tilted?2:diagonal?1:0;
        float[] edges=PLATES[kind][dir][mode];
        if(edges==null) {
            AABB plate=null;
            for(AABB box:shape.getVoxelShape(Direction.NORTH).toAabbs()) {
                if(centerPole(box))continue;
                plate=plate==null?box:plate.minmax(box);
            }
            if(plate==null)throw new IllegalArgumentException("No sign plate: "+shape);
            PLATES[kind][dir][mode]=edges=edges(plate,facing,mode);
        }
        return edges;
    }
    public static float[] pole(TrafficSignShape shape,Direction facing) {
        int kind=shape.ordinal(),dir=facing.get2DDataValue();
        float[] edges=POLES[kind][dir];
        if(edges==null) {
            double top=plateBottom(shape);
            POLES[kind][dir]=edges=top<=0?new float[0]:edges(new AABB(7D/16,0,7D/16,9D/16,top,9D/16),facing,0);
        }
        return edges;
    }
    private static boolean centerPole(AABB box) {
        return Math.abs(box.minX-7D/16)<1e-8 && Math.abs(box.maxX-9D/16)<1e-8
            && Math.abs(box.minZ-7D/16)<1e-8 && Math.abs(box.maxZ-9D/16)<1e-8;
    }
    public static float[] box(AABB box,Direction facing,boolean diagonal) {
        return edges(box,facing,diagonal?1:0);
    }
    public static double plateBottom(TrafficSignShape shape) {
        return shape==TrafficSignShape.SMALL_UPPER?.5:shape==TrafficSignShape.RECTANGLE_HORIZONTAL?.25:0;
    }
    public static float[] shearedBox(AABB box,Direction facing,double rise){return edges(box,facing,3,rise);}
    private static float[] edges(AABB box,Direction facing,int mode){return edges(box,facing,mode,0);}
    private static float[] edges(AABB box,Direction facing,int mode,double rise) {
        double[][] corners=new double[8][3];
        for(int i=0;i<8;i++) {
            double x=(i&1)==0?box.minX:box.maxX,y=(i&2)==0?box.minY:box.maxY,z=(i&4)==0?box.minZ:box.maxZ;
            if(mode==3)y+=(.5-z)*rise;
            if(mode==2) {
                // Same rigid X rotation and pivot as sign_post_mounted models.
                double dy=y-.5,dz=z-.5;
                y=.5+COS*dy+SIN*dz;z=1.25-SIN*dy+COS*dz;
            } else if(mode==1) {
                double dx=x-.5,dz=z-.5,cos=Math.sqrt(.5);
                x=.5+(dx-dz)*cos;z=.5+(dx+dz)*cos;
            }
            double dx=x-.5,dz=z-.5;
            switch(facing) {
                case EAST -> {x=.5-dz;z=.5+dx;}
                case SOUTH -> {x=.5-dx;z=.5-dz;}
                case WEST -> {x=.5+dz;z=.5-dx;}
                default -> {}
            }
            corners[i][0]=x;corners[i][1]=y;corners[i][2]=z;
        }
        float[] edges=new float[12*9];int out=0;
        for(int i=0;i<8;i++)for(int bit=1;bit<=4;bit<<=1)if((i&bit)==0) {
            double[] a=corners[i],b=corners[i|bit];
            double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            for(double value:a)edges[out++]=(float)value;
            for(double value:b)edges[out++]=(float)value;
            edges[out++]=(float)(dx/length);edges[out++]=(float)(dy/length);edges[out++]=(float)(dz/length);
        }
        return edges;
    }
}
