package com.destan.trafficengine.util;

/** Immutable ARGB value used by block tints and sign text. */
public record ColorValue(int argb) {
    public static final ColorValue WHITE = new ColorValue(0xFFFFFFFF), BLACK = new ColorValue(0xFF000000);
    public static ColorValue fromInt(int value) { return new ColorValue(value); }
    public int getAsARGB() { return argb; }
    public int getRed() { return argb >>> 16 & 255; }
    public int getGreen() { return argb >>> 8 & 255; }
    public int getBlue() { return argb & 255; }
    public static ColorValue pickBasedOnBrightness(ColorValue color, ColorValue dark, ColorValue light, float threshold) {
        double brightness = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return brightness <= threshold ? dark : light;
    }
}
