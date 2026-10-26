package com.destan.trafficengine.client.ber;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/** One duration map; file writes are coalesced outside the frame-rendering path. */
public final class BufferedTimerProperties {
    public static final int LIGHT_LIMIT=4096, PHASE_LIMIT=LIGHT_LIMIT*4;
    /** Access order preserves visible/recent signals while bounding long-session memory. */
    public static <K,V> Map<K,V> boundedMap(int limit){
        if(limit<1)throw new IllegalArgumentException("Positive cache limit required");
        return new java.util.LinkedHashMap<K,V>(16,.75F,true){
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest){return size()>limit;}
        };
    }
    private final Supplier<Path> file;
    private final Map<String, Long> durations;
    private boolean dirty;
    private long lastWrite;
    public BufferedTimerProperties(Supplier<Path> file, Map<String, Long> durations) {
        this.file = file;
        this.durations = durations;
    }
    public void load() {
        Path path = file.get();
        if (!Files.isRegularFile(path)) return;
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            values.load(reader);
            for (String key : values.stringPropertyNames()) {
                try { durations.put(key, Long.parseLong(values.getProperty(key))); }
                catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
    }
    public void remember(String key, long duration) {
        Long previous = durations.put(key, duration);
        if (previous == null || previous.longValue() != duration) dirty = true;
    }
    public void flush(boolean force) {
        long now = System.nanoTime();
        if (!dirty || !force && now - lastWrite < 3_000_000_000L) return;
        lastWrite = now;
        Path path = file.get();
        Properties values = new Properties();
        durations.forEach((key, value) -> values.setProperty(key, Long.toString(value)));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) { values.store(writer, ""); }
            dirty = false;
        } catch (Exception ignored) { }
    }
    public void reset() { dirty = false; lastWrite = 0; }
}
