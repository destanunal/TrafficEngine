package com.destan.trafficengine.client.gui;

import java.io.IOException;
import com.destan.trafficengine.data.RoadPatternNumbers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Client preferences: 318 preset bits and at most 128 immutable custom stencils; no world data. */
public final class PatternFavorites {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BitSet FAVORITES = new BitSet(318);
    private static final int MAX_CUSTOM = 128;
    private static final java.util.List<com.destan.trafficengine.data.PixelPattern> CUSTOM = new java.util.ArrayList<>();
    private static boolean loaded;
    private static PatternCatalog.Category lastCategory = PatternCatalog.Category.ALL;
    private PatternFavorites() {}
    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/trafficengine-patterns.properties");
    }
    public static boolean contains(int id) {
        load();
        id = RoadPatternNumbers.canonicalId(id);
        return id >= 0 && id < 318 && FAVORITES.get(id);
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(path())) return;
        Properties props = new Properties();
        try { if (Files.size(path()) > 65536) return; } catch (IOException ignored) { return; }
        try (var in = Files.newInputStream(path())) {
            props.load(in);
            try {
                lastCategory = PatternCatalog.Category.valueOf(props.getProperty("last_category", "ALL"));
            } catch (IllegalArgumentException ignored) {}
            for (int i = 0; i < MAX_CUSTOM; i++) {
                String value = props.getProperty("custom." + i);
                if (value == null || value.length() > 67) continue;
                String[] parts = value.split(":");
                if (parts.length != 4) continue;
                try {
                    long[] bits = new long[4];
                    for (int n = 0; n < 4; n++) bits[n] = Long.parseUnsignedLong(parts[n], 16);
                    var pattern = com.destan.trafficengine.data.PixelPattern.of(bits);
                    if (!pattern.isEmpty() && !CUSTOM.contains(pattern)) CUSTOM.add(pattern);
                } catch (NumberFormatException ignored) {}
            }
            for (String token : props.getProperty("favorites", "").split(",")) {
                try {
                    int id = RoadPatternNumbers.canonicalId(Integer.parseInt(token));
                    if (id >= 0 && id < 318) FAVORITES.set(id);
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) { LOGGER.warn("Could not read Traffic Engine favorites", e); }
    }
    public static void toggle(int id) {
        load();
        id = RoadPatternNumbers.canonicalId(id);
        if (id < 0 || id >= 318) return;
        FAVORITES.flip(id);
        save();
    }
    public static boolean contains(com.destan.trafficengine.data.PixelPattern pattern) {
        load(); return CUSTOM.contains(pattern);
    }
    public static boolean canAddCustom() { load(); return CUSTOM.size() < MAX_CUSTOM; }
    public static void toggle(com.destan.trafficengine.data.PixelPattern pattern) {
        load();
        if (pattern.isEmpty()) return;
        if (!CUSTOM.remove(pattern)) { if (CUSTOM.size() >= MAX_CUSTOM) return; CUSTOM.add(pattern); }
        save();
    }
    public static java.util.List<com.destan.trafficengine.data.PixelPattern> customPatterns() {
        load(); return java.util.List.copyOf(CUSTOM);
    }
    public static PatternCatalog.Category getLastCategory() {
        load();
        return lastCategory;
    }
    public static void setLastCategory(PatternCatalog.Category category) {
        load();
        if (lastCategory == category) return;
        lastCategory = category;
        save();
    }
    private static void save() {
        StringBuilder ids = new StringBuilder();
        for (int next = FAVORITES.nextSetBit(0); next >= 0; next = FAVORITES.nextSetBit(next + 1)) {
            if (!ids.isEmpty()) ids.append(',');
            ids.append(next);
        }
        Properties props = new Properties();
        for (int i = 0; i < CUSTOM.size(); i++) {
            long[] bits = CUSTOM.get(i).bits();
            String value = Long.toUnsignedString(bits[0], 16) + ":" + Long.toUnsignedString(bits[1], 16)
                + ":" + Long.toUnsignedString(bits[2], 16) + ":" + Long.toUnsignedString(bits[3], 16);
            props.setProperty("custom." + i, value);
        }
        props.setProperty("favorites", ids.toString());
        props.setProperty("last_category", lastCategory.name());
        try {
            Files.createDirectories(path().getParent());
            try (var out = Files.newOutputStream(path())) { props.store(out, "Traffic Engine client pattern preferences"); }
        } catch (IOException e) { LOGGER.warn("Could not save Traffic Engine favorites", e); }
    }
}
