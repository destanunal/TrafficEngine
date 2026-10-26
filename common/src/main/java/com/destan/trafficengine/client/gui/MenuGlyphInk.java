package com.destan.trafficengine.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.providers.UnihexProvider;

/** Source-pixel bounds, measured once per font glyph and cleared with the font cache. */
public interface MenuGlyphInk {
    record Ink(float top,float bottom) {}
    Ink trafficengine$ink();
    static Ink bitmap(NativeImage image,int x,int y,int width,int height,float scale,int ascent) {
        int first=height,last=-1;
        for(int row=0;row<height;row++)for(int col=0;col<width;col++) {
            if(image.getLuminanceOrAlpha(x+col,y+row)!=0){first=Math.min(first,row);last=row;break;}
        }
        float top=7-ascent;
        return last<first?new Ink(0,0):new Ink(top+first*scale,top+(last+1)*scale);
    }
    static Ink unihex(UnihexProvider.LineData lines) {
        int first=16,last=-1;
        for(int row=0;row<16;row++)if(lines.line(row)!=0){first=Math.min(first,row);last=row;}
        return last<first?new Ink(0,0):new Ink(first*.5F,(last+1)*.5F);
    }
}
