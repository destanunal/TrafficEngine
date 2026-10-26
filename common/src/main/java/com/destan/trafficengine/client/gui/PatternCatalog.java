package com.destan.trafficengine.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.data.RoadPatternNumbers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-only choices and captions. Persistent IDs stay stable across catalogue changes. */
public final class PatternCatalog {
    public enum Category {
        ALL, LINES, ARROWS, LETTERS, NUMBERS, SYMBOLS, FAVORITES;
        public Component title() { return Component.translatable("gui.trafficengine.patterns.category." + name().toLowerCase(Locale.ROOT)); }
    }
    public static final class Pattern {
        private final com.destan.trafficengine.data.PixelPattern custom;
        private final int id;
        private final Category category;
        Pattern(int id, Category category) { this(id, category, com.destan.trafficengine.data.PixelPattern.EMPTY); }
        Pattern(int id, Category category, com.destan.trafficengine.data.PixelPattern custom) { this.id = id; this.category = category; this.custom = custom; }
        public com.destan.trafficengine.data.PixelPattern custom() { return custom; }
        public int id() { return id; }
        public int displayNumber() { return RoadPatternNumbers.displayNumber(id); }
        public Category category() { return category; }
        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID,
                id == 0 ? "textures/block/sign_blank.png" : "textures/block/patterns/" + id + ".png");
        }
        public Component name() {
            if (!custom.isEmpty()) return Component.translatable("gui.trafficengine.patterns.custom", -id);
            if (id == 0 || id == 1 || id == 246 || id >= 297) {
                return Component.translatable("gui.trafficengine.patterns.name." + id);
            }
            if (category == Category.LETTERS) return Component.literal(Character.toString((char)('A' + id - 261)));
            if (category == Category.NUMBERS) return Component.literal(Integer.toString(id - 287));
            return Component.translatable("gui.trafficengine.patterns.number", displayNumber());
        }
        public boolean matches(String query) {
            String needle = query.strip().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) return true;
            if (needle.startsWith("#")) needle = needle.substring(1);
            return Integer.toString(displayNumber()).equals(needle) || name().getString().toLowerCase(Locale.ROOT).contains(needle)
                || category.title().getString().toLowerCase(Locale.ROOT).contains(needle);
        }
    }
    public static Pattern custom(int index, com.destan.trafficengine.data.PixelPattern pattern) {
        return new Pattern(-index - 1, Category.FAVORITES, pattern);
    }
    public static List<Pattern> all() { return create(); }
    private PatternCatalog() {}
    private static List<Pattern> create() {
        List<Pattern> entries = new ArrayList<>();
        entries.add(new Pattern(0, Category.SYMBOLS));
        for (int id = 1; id <= 314; id++) {
            if (!RoadPatternNumbers.isVisible(id)) continue;
            entries.add(new Pattern(id, category(id)));
            if (id == 1) entries.add(new Pattern(316, Category.LINES));
            if (id == 246) entries.add(new Pattern(317, Category.ARROWS));
            if (id == 299) entries.add(new Pattern(315, Category.SYMBOLS));
        }
        return List.copyOf(entries);
    }
    private static Category category(int id) {
        // IDs 181-242 are wide road bends, junctions and hatching, not arrows.
        if (id <= 242 || id == 259 || id == 260) return Category.LINES;
        if (id <= 258) return Category.ARROWS;
        if (id <= 286) return Category.LETTERS;
        if (id <= 296) return Category.NUMBERS;
        return Category.SYMBOLS;
    }
    public static Pattern byId(int id) {
        if (id < 0 || id > 317) return null;
        return new Pattern(id, id == 0 ? Category.SYMBOLS : id == 316 ? Category.LINES
            : id == 317 ? Category.ARROWS : category(id));
    }
}
