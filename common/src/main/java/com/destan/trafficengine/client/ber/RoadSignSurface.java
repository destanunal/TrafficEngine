package com.destan.trafficengine.client.ber;

/** One small atlas shared by every directional sign; generated only on texture load/reload. */
final class RoadSignSurface {
    static final int TILE = 64, WIDTH = 512, HEIGHT = 256, LEVELS = 4;
    static final int WHITE = 16;
    static final double PAD = 8, CORNER = 5;
    private static final int[] BACKGROUNDS = {0xFF176B48, 0xFF174C9B, 0xFF784A2D, 0xFFCDD1CB};
    private static final int[] PANELS = {0xFFD6D9D3, 0xFF174C9B, 0xFF784A2D, 0xFF176B48};
    static int frame(int theme, int style) { return Math.max(0, Math.min(3, theme)) * 3 + Math.max(0, Math.min(2, style)); }
    static int panel(int panel) { return 12 + Math.max(0, Math.min(3, panel - 1)); }
    static float u(int tile, double pixel) { return (float)((tile % 8 * TILE + pixel) / WIDTH); }
    static float v(int tile, double pixel) { return (float)((tile / 8 * TILE + pixel) / HEIGHT); }
    // Equal geometry/UV splits ensure adjacent patches share exact float vertices.
    static double position(int split, double lo, double hi, boolean joinedLo, boolean joinedHi) {
        double corner = Math.min(CORNER, (hi - lo) / 2);
        return switch(split) {
            case 0 -> joinedLo ? lo : lo - PAD;
            case 1 -> joinedLo ? lo : lo + corner;
            case 2 -> joinedHi ? hi : hi - corner;
            default -> joinedHi ? hi : hi + PAD;
        };
    }
    static double texel(int split, boolean joinedLo, boolean joinedHi) {
        return switch(split) {
            case 0 -> joinedLo ? 32 : 0;
            case 1 -> joinedLo ? 32 : 26;
            case 2 -> joinedHi ? 32 : 38;
            default -> joinedHi ? 32 : TILE;
        };
    }
    static int[][] pixels() {
        int[][] levels = new int[LEVELS + 1][];
        int[] base = levels[0] = new int[WIDTH * HEIGHT];
        for (int tile = 0; tile <= WHITE; tile++) {
            int tx = tile % 8 * TILE, ty = tile / 8 * TILE;
            for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) {
                // Four samples per axis preserve the sub-texel 0.6-unit stroke at level zero.
                int alpha = 0, red = 0, green = 0, blue = 0;
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                    int sample = sample(tile, (x + (sx + .5) / 4) / 2 - PAD,
                            (y + (sy + .5) / 4) / 2 - PAD);
                    int a = sample >>> 24;
                    alpha += a; red += ((sample >> 16) & 255) * a;
                    green += ((sample >> 8) & 255) * a; blue += (sample & 255) * a;
                }
                int rgb = alpha == 0 ? sample(tile, -1, -1) & 0xFFFFFF
                        : (red / alpha << 16) | (green / alpha << 8) | blue / alpha;
                base[(ty + y) * WIDTH + tx + x] = ((alpha + 8) / 16 << 24) | rgb;
            }
        }
        dilate(base,WIDTH,HEIGHT,TILE);
        for (int level = 1; level <= LEVELS; level++) {
            int w = WIDTH >> level, h = HEIGHT >> level, prevW = w * 2;
            int[] prev = levels[level - 1], next = levels[level] = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int p = y * 2 * prevW + x * 2;
                next[y * w + x] = average(prev[p], prev[p + 1], prev[p + prevW], prev[p + prevW + 1]);
            }
            dilate(next,w,h,TILE>>level);
        }
        return levels;
    }
    /** Extrude nearest visible RGB separately at every mip; alpha/physical coverage is unchanged. */
    private static void dilate(int[] pixels,int width,int height,int tileSize) {
        for(int tile=0;tile<WHITE;tile++) {
            int tx=tile%8*tileSize,ty=tile/8*tileSize;
            for(int y=ty;y<ty+tileSize;y++) {
                int first=-1,last=-1;
                for(int x=tx;x<tx+tileSize;x++)if(pixels[y*width+x]>>>24!=0){if(first<0)first=x;last=x;}
                if(first<0)continue;
                for(int x=tx;x<first;x++)pixels[y*width+x]=pixels[y*width+first]&0xFFFFFF;
                for(int x=last+1;x<tx+tileSize;x++)pixels[y*width+x]=pixels[y*width+last]&0xFFFFFF;
            }
            int first=-1,last=-1;
            for(int y=ty;y<ty+tileSize;y++)if(pixels[y*width+tx+tileSize/2]>>>24!=0){if(first<0)first=y;last=y;}
            if(first<0)continue;
            for(int x=tx;x<tx+tileSize;x++) {
                for(int y=ty;y<first;y++)pixels[y*width+x]=pixels[first*width+x]&0xFFFFFF;
                for(int y=last+1;y<ty+tileSize;y++)pixels[y*width+x]=pixels[last*width+x]&0xFFFFFF;
            }
        }
    }
    static int average(int c0, int c1, int c2, int c3) {
        int a0=c0>>>24, a1=c1>>>24, a2=c2>>>24, a3=c3>>>24, alpha=a0+a1+a2+a3;
        if (alpha == 0) return c0 & 0xFFFFFF; // Retain edge RGB in transparent gutters: no dark halos.
        int color = (alpha + 2) / 4 << 24;
        for (int shift=0; shift<=16; shift+=8) {
            int sum=((c0>>>shift)&255)*a0+((c1>>>shift)&255)*a1+((c2>>>shift)&255)*a2+((c3>>>shift)&255)*a3;
            color |= (sum + alpha/2) / alpha << shift;
        }
        return color;
    }
    private static int sample(int tile, double x, double y) {
        if (tile == WHITE) return x>=0 && x<=16 && y>=0 && y<=16 ? 0xFFFFFFFF : 0x00FFFFFF;
        boolean panel = tile >= 12;
        int style = panel ? 1 : tile % 3;
        int fill = panel ? PANELS[tile - 12] : BACKGROUNDS[tile / 3];
        int edge = panel ? (tile == 12 ? 0xFF17252E : 0xFFFFFFFF)
                : style == 1 ? 0xFFFFFFFF : style == 2 ? 0xFF53616B : fill;
        double radius = panel ? .8 : 4;
        if (!inside(x,y,0,radius)) return edge & 0xFFFFFF;
        if (panel) return inside(x,y,.6,Math.max(0,radius-.6)) ? fill : edge;
        if (style == 0) return fill;
        if (style == 1) return inside(x,y,1.3,radius-1.3) ? fill : edge;
        if (!inside(x,y,.8,radius-.8)) return edge;
        return inside(x,y,2.8,radius-2.8) ? fill : 0xFFA8B4BC;
    }
    private static boolean inside(double x, double y, double inset, double radius) {
        if (x < inset || y < inset || x > 16-inset || y > 16-inset) return false;
        double cx=Math.max(inset+radius,Math.min(16-inset-radius,x));
        double cy=Math.max(inset+radius,Math.min(16-inset-radius,y));
        double dx=x-cx,dy=y-cy;
        return dx*dx+dy*dy<=radius*radius;
    }
}
