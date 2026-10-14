package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.item.RoadStrokeGeometry;
import net.minecraft.client.gui.GuiGraphics;

/** Shared automatic-line preview: pattern #316 is 2/16 wide, pattern #1 is 4/16 wide. */
public final class RoadLinePreview {
    private RoadLinePreview() {}
    public static void straight(GuiGraphics g,int x,int y,int w,int h,int style,int lanes,int gap,int position,boolean vertical,int tint) {
        int cross=vertical?w:h,along=vertical?h:w;
        double unit=vertical?w:16;
        int thickness=Math.max(1,(int)Math.round(unit*RoadStrokeGeometry.lineWidth(style)));
        int spacing=(int)Math.round(unit*0.75),band=(int)Math.round(unit*0.1875);
        int shift=(int)Math.round(RoadStrokeGeometry.positionOffset(position)*(vertical?unit:42));
        int limit=cross/2-(lanes/2)*spacing-(style==2?band:0)-thickness/2;
        shift=Math.max(-limit,Math.min(limit,shift));
        for(int lane=0;lane<lanes;lane++) {
            int center=cross/2+(lane-lanes/2)*spacing+shift;
            if(style==2) {
                stripe(g,x,y,4,along-4,center-band,thickness,vertical,tint);
                stripe(g,x,y,4,along-4,center+band,thickness,vertical,tint);
            } else if(style==1) {
                double step=(along-8)/12.0;
                for(int start=0;start+2<=12;start+=2+(gap==4?4:2))
                    stripe(g,x,y,4+(int)Math.round(start*step),4+(int)Math.round((start+2)*step),center,thickness,vertical,tint);
            } else stripe(g,x,y,4,along-4,center,thickness,vertical,tint);
        }
    }
    private static void stripe(GuiGraphics g,int x,int y,int from,int to,int center,int thickness,boolean vertical,int tint) {
        int a=center-thickness/2,b=a+thickness;
        if(vertical) g.fill(x+a,y+from,x+b,y+to,tint);
        else g.fill(x+from,y+a,x+to,y+b,tint);
    }
}
