package com.destan.trafficengine.util;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
public final class Clipboard {
    private static final Map<Class<?>,Object> values=new HashMap<>();
    public static <T> void put(Class<T> type,T value) {values.put(type,value);}
    public static <T> Optional<T> get(Class<T> type) {return Optional.ofNullable(type.cast(values.get(type)));}
    public static boolean contains(Class<?> type) {return values.containsKey(type);}
}
