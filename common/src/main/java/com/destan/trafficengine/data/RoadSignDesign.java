package com.destan.trafficengine.data;

import net.minecraft.nbt.CompoundTag;

/** Validated display settings shared by the editor, saved sign and renderer. */
public record RoadSignDesign(int rows, int alignment, int textSize, int ink, int frame,
        boolean bold, int arrowPosition, boolean dividers, int panelColors) {
    public static final int ARROWS_LEFT = 0, ARROWS_CENTER = 1, ARROWS_RIGHT = 2;
    public static final int NO_PANEL = 0, WHITE_PANEL = 1, BLUE_PANEL = 2, BROWN_PANEL = 3, GREEN_PANEL = 4;
    public static final RoadSignDesign DEFAULT = new RoadSignDesign(3, 0, 100, 0, 2, false, false, false);

    public RoadSignDesign {
        arrowPosition = Math.max(ARROWS_LEFT, Math.min(ARROWS_RIGHT, arrowPosition));
        rows = Math.max(1, Math.min(3, rows));
        alignment = Math.max(0, Math.min(2, alignment));
        textSize = Math.max(70, Math.min(160, textSize));
        ink = Math.max(0, Math.min(3, ink));
        frame = Math.max(0, Math.min(2, frame));
        int validPanels = 0;
        for (int row = 0; row < 3; row++) {
            int color = (panelColors >>> (row * 3)) & 7;
            if (color <= GREEN_PANEL) validPanels |= color << (row * 3);
        }
        panelColors = validPanels;
    }

    public RoadSignDesign(int rows, int alignment, int textSize, int ink, int frame,
            boolean bold, boolean leftArrows, boolean dividers) {
        this(rows, alignment, textSize, ink, frame, bold, leftArrows ? ARROWS_LEFT : ARROWS_RIGHT, dividers, 0);
    }

    public RoadSignDesign(int rows, int alignment, int textSize, int ink, int frame,
            boolean bold, boolean leftArrows, boolean dividers, int panelColors) {
        this(rows, alignment, textSize, ink, frame, bold, leftArrows ? ARROWS_LEFT : ARROWS_RIGHT, dividers, panelColors);
    }

    public boolean leftArrows() { return arrowPosition == ARROWS_LEFT; }
    public boolean centeredArrows() { return arrowPosition == ARROWS_CENTER; }

    public int visibleRows(boolean large) { return large ? rows : Math.min(2, rows); }

    public RoadSignDesign withRows(int count) {
        return new RoadSignDesign(count, alignment, textSize, ink, frame, bold, arrowPosition, dividers, panelColors);
    }

    public int panelColor(int row) {
        return row >= 0 && row < 3 ? (panelColors >>> (row * 3)) & 7 : NO_PANEL;
    }

    public RoadSignDesign withPanelColor(int row, int color) {
        if (row < 0 || row > 2) return this;
        color = Math.max(NO_PANEL, Math.min(GREEN_PANEL, color));
        return new RoadSignDesign(rows, alignment, textSize, ink, frame, bold, arrowPosition, dividers,
                (panelColors & ~(7 << (row * 3))) | (color << (row * 3)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("layout_version", 2); tag.putInt("rows", rows); tag.putInt("alignment", alignment); tag.putInt("text_size", textSize);
        tag.putInt("ink", ink); tag.putInt("frame", frame); tag.putBoolean("bold", bold);
        tag.putBoolean("left_arrows", leftArrows()); tag.putInt("arrow_position", arrowPosition); tag.putBoolean("dividers", dividers);
        tag.putInt("panel_colors", panelColors);
        return tag;
    }

    public static RoadSignDesign load(CompoundTag tag) {
        if (tag.isEmpty()) return DEFAULT;
        int panels = tag.getInt("panel_colors");
        if (!tag.contains("panel_colors")) {
            int white = tag.getInt("white_panels") & 7;
            for (int row = 0; row < 3; row++) if ((white & (1 << row)) != 0)
                panels |= WHITE_PANEL << (row * 3);
        }
        return new RoadSignDesign(tag.contains("rows") ? tag.getInt("rows") : 3,
                tag.getInt("alignment"), tag.contains("text_size") ? tag.getInt("text_size") : 100,
                tag.getInt("ink"), tag.contains("frame") ? tag.getInt("frame") : 2,
                tag.getBoolean("bold"), tag.contains("arrow_position") ? tag.getInt("arrow_position")
                        : tag.getBoolean("left_arrows") ? ARROWS_LEFT : ARROWS_RIGHT, tag.getBoolean("dividers"),
                panels);
    }
}
