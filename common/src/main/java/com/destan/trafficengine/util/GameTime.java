package com.destan.trafficengine.util;
import net.minecraft.world.level.Level;
public record GameTime(long ticks) {
    public static final int TICKS_PER_DAY=24000, DAYTIME_OFFSET=6000;
    public GameTime(Level level) {this(level.getDayTime());}
    public String format(ETimeFormat format) {
        if (format==ETimeFormat.TICKS) return Long.toString(Math.floorMod(ticks,TICKS_PER_DAY));
        long shifted=Math.floorMod(ticks+DAYTIME_OFFSET,TICKS_PER_DAY);
        int hour=(int)(shifted*24/TICKS_PER_DAY), minute=(int)(shifted*1440/TICKS_PER_DAY)%60;
        return format==ETimeFormat.HOURS_12 ? String.format("%02d:%02d %s",hour%12==0?12:hour%12,minute,hour>=12?"PM":"AM") : String.format("%02d:%02d",hour,minute);
    }
    public boolean isBetweenDaily(GameTime start,GameTime end) {
        long now=Math.floorMod(ticks,TICKS_PER_DAY),a=Math.floorMod(start.ticks,TICKS_PER_DAY),b=Math.floorMod(end.ticks,TICKS_PER_DAY);
        return a<=b ? now>=a && now<=b : now>=a || now<=b;
    }
}
