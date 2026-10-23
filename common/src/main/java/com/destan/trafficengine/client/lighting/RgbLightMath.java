package com.destan.trafficengine.client.lighting;

/** Three independent, bounded light channels. No world or renderer state. */
public final class RgbLightMath {
    private RgbLightMath() {}
    public static int pack(int r, int g, int b) { return (clamp(r) << 8) | (clamp(g) << 4) | clamp(b); }
    private static int clamp(int value) { return Math.max(0, Math.min(15, value)); }
    public static int red(int rgb) { return rgb >> 8 & 15; }
    public static int green(int rgb) { return rgb >> 4 & 15; }
    public static int blue(int rgb) { return rgb & 15; }
    public static int maximum(int rgb) { return Math.max(red(rgb), Math.max(green(rgb), blue(rgb))); }
    public static int merge(int a, int b) { return pack(Math.max(red(a),red(b)),Math.max(green(a),green(b)),Math.max(blue(a),blue(b))); }
    public static int decay(int rgb) { return pack(red(rgb)-1,green(rgb)-1,blue(rgb)-1); }
    public static int emission(int color, int strength) {
        int r=color>>16&255,g=color>>8&255,b=color&255;
        int max=Math.max(r,Math.max(g,b));
        return max==0 ? 0 : pack(Math.round((float)r*strength/max),Math.round((float)g*strength/max),Math.round((float)b*strength/max));
    }
    /** Embeddium stores vertex colours as ABGR, with red in the low byte. */
    public static int tintAbgr(int color, float red, float green, float blue) {
        int r = Math.round((color & 255) * red);
        int g = Math.round((color >>> 8 & 255) * green);
        int b = Math.round((color >>> 16 & 255) * blue);
        return (color & 0xFF000000) | (b << 16) | (g << 8) | r;
    }
    public static float factor(int channel, int rgb, int blockLight, int skyLight) {
        float colored=maximum(rgb), white=Math.max(0,blockLight-colored)+Math.max(0,skyLight);
        if (colored==0) return 1;
        // Keep Minecraft's ambient floor, while daytime and other white lights soften the tint.
        return 0.12F+0.88F*(white+channel)/(white+colored);
    }
}
