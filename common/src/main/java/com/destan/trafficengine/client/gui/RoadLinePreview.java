package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.item.RoadPaintPlan;
import com.destan.trafficengine.item.RoadStrokeGeometry;
import net.minecraft.client.gui.GuiGraphics;

/** Fits every selected lane into the preview, preserving the narrow/wide pattern proportions. */
public final class RoadLinePreview {
    private RoadLinePreview() {}
    public static void straight(GuiGraphics g,int x,int y,int w,int h,int style,int lanes,int gap,int position,boolean vertical,int tint) {
        lanes=RoadPaintPlan.normalizeWidth(lanes);
        int cross=vertical?w:h,along=vertical?h:w;
        double unit=lanes==1?(vertical?w:16):Math.min(16,(cross-8.0)/lanes);
        int thickness=Math.max(1,(int)Math.round(unit*RoadStrokeGeometry.lineWidth(style)));
        int spacing=Math.max(1,(int)Math.round(unit)),band=Math.max(1,(int)Math.round(unit*0.1875));
        int shift=lanes>1?0:(int)Math.round(RoadStrokeGeometry.positionOffset(position)*(vertical?unit:42));
        int limit=Math.max(0,cross/2-(lanes/2)*spacing-(style==2?band:0)-thickness/2-2);
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
        if(vertical)g.fill(x+a,y+from,x+b,y+to,tint);
        else g.fill(x+from,y+a,x+to,y+b,tint);
    }
}
