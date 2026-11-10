package com.destan.trafficengine.client.ber;

/** Regression of actual generated pixels, mip coverage, patch joins and cache bounds. */
public final class SignPanelCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        long start=System.nanoTime();
        int[][] levels=RoadSignSurface.pixels();
        int bytes=0;
        for(int level=0;level<levels.length;level++) {
            int w=RoadSignSurface.WIDTH>>level,h=RoadSignSurface.HEIGHT>>level;
            check(levels[level].length==w*h,"Mip sizes match the uploaded texture");bytes+=w*h*4;
            int tile=RoadSignSurface.TILE>>level;
            for(int id=0;id<=16;id++) {
                int x=id%8*tile,y=id/8*tile;
                check((levels[level][(y+tile/2)*w+x+tile/2]>>>24)>200,"Interior stays opaque through every mip level");
                for(int edge=0;edge<tile;edge++) {
                    check((levels[level][y*w+x+edge]>>>24)==0,"Top atlas gutter stays transparent");
                    check((levels[level][(y+tile-1)*w+x+edge]>>>24)==0,"Bottom atlas gutter cannot bleed the white patch");
                    check((levels[level][(y+edge)*w+x]>>>24)==0,"Left atlas gutter stays transparent");
                    check((levels[level][(y+edge)*w+x+tile-1]>>>24)==0,"Right atlas gutter stays transparent");
                }
            }
        }
        check(bytes<700_000,"One shared mip atlas is bounded below 700 KB, regardless of sign count");
        for(int style=0;style<16;style++)for(int level=0;level<levels.length;level++) {
            int w=RoadSignSurface.WIDTH>>level,tile=64>>level;
            int tx=style%8*tile,ty=style/8*tile;
            double alpha=0;
            for(int y=0;y<tile;y++)for(int x=0;x<tile;x++)alpha+=levels[level][(ty+y)*w+tx+x]>>>24;
            double normalized=alpha*(1<<(level*2));
            double base=0;int bx=style%8*64,by=style/8*64;
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)base+=levels[0][(by+y)*RoadSignSurface.WIDTH+bx+x]>>>24;
            check(Math.abs(normalized-base)<base*.012,"Minification preserves physical coverage rather than inflating the frame");
        }
        for(double width:new double[]{8,16,72,192,384})for(int joins=0;joins<4;joins++) {
            boolean left=(joins&1)!=0,right=(joins&2)!=0;
            double previous=Double.NEGATIVE_INFINITY;
            for(int split=0;split<4;split++) {
                double x=RoadSignSurface.position(split,0,width,left,right);
                check(x>=previous,"Nine-patch splits never cross even on tiny panels");previous=x;
                float uv=RoadSignSurface.u(0,RoadSignSurface.texel(split,left,right));
                check(uv>=0&&uv<=64F/512,"All samples remain within their atlas tile");
            }
            if(left)check(RoadSignSurface.position(0,0,width,true,right)==0&&RoadSignSurface.texel(0,true,right)==32,"Joined left edge samples the interior, with no vertical frame seam");
            if(right)check(RoadSignSurface.position(3,0,width,left,true)==width&&RoadSignSurface.texel(3,left,true)==32,"Joined right edge samples the interior, with no vertical frame seam");
        }
        int mixed=RoadSignSurface.average(0x00FFFFFF,0x00FFFFFF,0xFFFFFFFF,0xFFFFFFFF);
        check((mixed&0xFFFFFF)==0xFFFFFF&&mixed>>>24==128,"Transparent pixels do not create black halos");
        var cache=new RoadSignGraphics.LayoutCache();Object world=new Object();var first=cache.get(world,0);
        for(int i=1;i<=256;i++)cache.get(world,i);
        check(cache.size()==256,"Layout count stays bounded");
        check(cache.get(world,0)!=first,"Oldest sign layout can be evicted");
        check(cache.get(new Object(),0)!=first&&cache.size()==1,"World changes invalidate the layout cache");
        System.out.println("PASS SignPanel: "+checks+" mip/coverage/patch/cache checks; "+bytes+" shared texture bytes, "+((System.nanoTime()-start)/1_000_000)+" ms (not an FPS benchmark)");
    }
}
