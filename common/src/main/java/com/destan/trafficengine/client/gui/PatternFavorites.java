package com.destan.trafficengine.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Client favorites use at most 318 bits, independently of brush and world data. */
public final class PatternFavorites {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BitSet FAVORITES = new BitSet(318);
    private static boolean loaded;
    private static PatternCatalog.Category lastCategory = PatternCatalog.Category.ALL;
    private PatternFavorites() {}
    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/trafficengine-patterns.properties");
    }
    public static boolean contains(int id) {
        load();
        return id >= 0 && id < 318 && FAVORITES.get(id);
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(path())) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(path())) {
            props.load(in);
            try {
                lastCategory = PatternCatalog.Category.valueOf(props.getProperty("last_category", "ALL"));
            } catch (IllegalArgumentException ignored) {}
            for (String token : props.getProperty("favorites", "").split(",")) {
                try {
                    int id = Integer.parseInt(token);
                    if (id >= 0 && id < 318) FAVORITES.set(id);
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) { LOGGER.warn("Could not read Traffic Engine favorites", e); }
    }
    public static void toggle(int id) {
        load();
        if (id < 0 || id >= 318) return;
        FAVORITES.flip(id);
        save();
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
        props.setProperty("favorites", ids.toString());
        props.setProperty("last_category", lastCategory.name());
        try {
            Files.createDirectories(path().getParent());
            try (var out = Files.newOutputStream(path())) { props.store(out, "Traffic Engine client pattern preferences"); }
        } catch (IOException e) { LOGGER.warn("Could not save Traffic Engine favorites", e); }
    }
}
