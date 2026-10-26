package com.destan.trafficengine.client.model;

/** Immutable shallow octagonal rim. Built once; no trigonometry/arrays per rendered bulb. */
public final class TrafficLensGeometry {
    public static final float DEPTH=0.18F;
    public static final float[][] SIDES=new float[8][12];
    public static final float[][] NORMALS=new float[8][3];
    static {
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
            float x0=2+2*(float)Math.cos(a),y0=-2+2*(float)Math.sin(a);
            float x1=2+2*(float)Math.cos(b),y1=-2+2*(float)Math.sin(b);
            SIDES[i]=new float[]{x1,y1,1,x0,y0,1,x0,y0,1-DEPTH,x1,y1,1-DEPTH};
            NORMALS[i]=new float[]{(float)Math.cos((a+b)/2),(float)Math.sin((a+b)/2),0};
        }
    }
    private TrafficLensGeometry(){}
}
