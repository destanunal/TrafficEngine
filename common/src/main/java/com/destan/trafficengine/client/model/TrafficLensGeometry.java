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
    // Finite layouts (one to three digits, narrow digit 1). Never grows at runtime.
    private static final float[][] COUNTDOWN_SCALES=new float[4][4];
    static {
        for(int count=1;count<=3;count++)for(int ones=0;ones<=count;ones++) {
            float width=(count-ones)*1.6F+ones*.4F+(count-1)*.8F;
            COUNTDOWN_SCALES[count][ones]=fitCountdown(width,3.2F);
        }
    }
    public static float countdownScale(int count,int narrowDigits) {return COUNTDOWN_SCALES[count][narrowDigits];}
    private static float fitCountdown(float width,float height) {
        // The bounding rectangle must fit inside the lens, not just inside its width.
        return Math.min(1F,3.65F/(float)Math.sqrt(width*width+height*height));
    }
    private TrafficLensGeometry(){}
}
