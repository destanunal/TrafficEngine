package com.destan.trafficengine.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;

/** Explicit opt-in for our curved tube segments. Used only while a model is loaded/baked. */
public final class SmoothModelAngles {
    public static final String MARKER="trafficengine:smooth_curve";
    private SmoothModelAngles(){}
    public static boolean supported(JsonObject rotation){return GsonHelper.getAsBoolean(rotation,MARKER,false);}
    public static float angle(JsonObject rotation){
        float angle=GsonHelper.getAsFloat(rotation,"angle");
        if(!Float.isFinite(angle)||Math.abs(angle)>90||GsonHelper.getAsBoolean(rotation,"rescale",false))
            throw new JsonParseException("Traffic Engine curve rotation must be finite, within 90 degrees and unscaled");
        return angle;
    }
}
