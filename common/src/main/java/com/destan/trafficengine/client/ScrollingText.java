package com.destan.trafficengine.client;

import java.util.function.ToIntFunction;

/** Clips at complete Unicode glyphs so moving text never leaves its display. */
public final class ScrollingText {
    public record Window(String text,float x) {}
    private ScrollingText() {}
    public static Window window(String text,ToIntFunction<String> measure,float width,double ticks,int speed) {
        if(text.isEmpty() || width<=0)return new Window("",0);
        float cycle=measure.applyAsInt(text)+width+12;
        float x=width-(float)((ticks*Math.max(1,Math.min(3,speed))*0.25)%cycle);
        int start=0,end=0;float startX=x;
        while(end<text.length()) {
            int next=end+Character.charCount(text.codePointAt(end));
            float advance=measure.applyAsInt(text.substring(end,next));
            if(x<0) { start=next;startX=x+advance; }
            else if(x+advance>width)break;
            x+=advance;end=next;
        }
        return new Window(text.substring(Math.min(start,end),end),startX);
    }
}
