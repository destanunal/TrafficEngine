package com.destan.trafficengine.util;

/** Existing sign images are at most 32 by 32; validate before any native allocation. */
public final class SignTextureLimits {
    public static final int MAX_ENCODED_BYTES = 65536;
    private SignTextureLimits() {}
    private static int integer(byte[] a, int p) {
        return (a[p] & 255) << 24 | (a[p + 1] & 255) << 16 | (a[p + 2] & 255) << 8 | a[p + 3] & 255;
    }
    public static boolean validPng(byte[] png, int width, int height) {
        return width > 0 && height > 0 && width <= 32 && height <= 32
            && png != null && png.length >= 33 && png.length <= MAX_ENCODED_BYTES
            && integer(png, 0) == 0x89504E47 && integer(png, 4) == 0x0D0A1A0A
            && integer(png, 8) == 13 && integer(png, 12) == 0x49484452
            && integer(png, 16) == width && integer(png, 20) == height;
    }
}
