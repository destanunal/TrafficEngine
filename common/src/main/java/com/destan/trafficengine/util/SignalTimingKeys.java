package com.destan.trafficengine.util;

/** Small lazy cache owned by one countdown signal; keys never retain the world/entity. */
public final class SignalTimingKeys {
    private final String light;
    private String[] phases;
    public SignalTimingKeys(String dimension,long position,int face,boolean horizontal){
        String prefix=dimension+"_"+position;
        // Preserve existing persisted horizontal keys and independent front/back vertical keys.
        light=horizontal?prefix:prefix+"_"+face;
    }
    public String light(){return light;}
    public String phase(int color){
        if(color<0||color>4)throw new IllegalArgumentException("Unsupported timing colour: "+color);
        if(phases==null)phases=new String[5];
        String key=phases[color];
        if(key==null)phases[color]=key=light+"_"+color;
        return key;
    }
}
