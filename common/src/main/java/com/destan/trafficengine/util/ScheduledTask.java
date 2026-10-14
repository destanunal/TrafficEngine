package com.destan.trafficengine.util;
import java.util.*;
import java.util.function.BiFunction;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.world.level.Level;
/** Road construction work advances on the server thread and releases its data on stop. */
public final class ScheduledTask<T> {
    private static final List<ScheduledTask<?>> tasks=new ArrayList<>();
    static {TickEvent.SERVER_POST.register(server -> tasks.removeIf(task -> !task.tick()));LifecycleEvent.SERVER_STOPPED.register(server -> tasks.clear());}
    private final T data;private final Level level;private final int delay,max;private final BiFunction<T,ScheduledTaskContext,Boolean> action;
    private int ticks,iteration;
    private ScheduledTask(T data,Level level,int delay,int max,BiFunction<T,ScheduledTaskContext,Boolean> action) {this.data=data;this.level=level;this.delay=Math.max(1,delay);this.max=max;this.action=action;}
    public static <T> void create(T data,Level level,int delay,int max,BiFunction<T,ScheduledTaskContext,Boolean> action) {if(max>0)tasks.add(new ScheduledTask<>(data,level,delay,max,action));}
    private boolean tick() {if(++ticks%delay!=0)return true;boolean keep=action.apply(data,new ScheduledTaskContext(level,iteration));return ++iteration<max && keep;}
    public record ScheduledTaskContext(Level level,int iteration) {}
}
