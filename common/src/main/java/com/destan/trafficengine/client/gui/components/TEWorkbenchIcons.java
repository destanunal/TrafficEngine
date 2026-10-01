package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.ModGuiIcons;
import net.minecraft.client.gui.GuiGraphics;

/** Small, crisp tool outlines use the theme's foreground color instead of black texture pixels. */
public final class TEWorkbenchIcons {
    private TEWorkbenchIcons() {}
    public static void draw(GuiGraphics g, ModGuiIcons icon, int x, int y, int color) {
        switch (icon) {
            case CANCEL, DISCARD_FILE -> {
                line(g, x + 3, y + 3, x + 12, y + 12, color);
                line(g, x + 12, y + 3, x + 3, y + 12, color);
            }
            case CHECK -> {
                line(g, x + 2, y + 7, x + 6, y + 11, color);
                line(g, x + 6, y + 11, x + 13, y + 3, color);
                line(g, x + 2, y + 8, x + 6, y + 12, color);
                line(g, x + 6, y + 12, x + 13, y + 4, color);
            }
            case MOVE_UP, MOVE_DOWN -> {
                boolean up = icon == ModGuiIcons.MOVE_UP;
                g.fill(x + 7, y + 4, x + 9, y + 12, color);
                for (int i = 0; i < 4; i++) {
                    int yy = up ? y + 3 + i : y + 12 - i;
                    g.fill(x + 7 - i, yy, x + 9 + i, yy + 1, color);
                }
            }
            case ADD -> { g.fill(x + 2, y + 7, x + 14, y + 9, color); g.fill(x + 7, y + 2, x + 9, y + 14, color); }
            case EDIT -> {
                line(g, x + 3, y + 11, x + 11, y + 3, color);
                line(g, x + 5, y + 13, x + 13, y + 5, color);
                line(g, x + 11, y + 3, x + 13, y + 5, color);
                line(g, x + 3, y + 11, x + 5, y + 13, color);
                g.fill(x + 2, y + 13, x + 5, y + 15, color);
            }
            case ERASE -> {
                line(g, x + 2, y + 8, x + 8, y + 2, color);
                line(g, x + 8, y + 2, x + 13, y + 7, color);
                line(g, x + 13, y + 7, x + 7, y + 13, color);
                line(g, x + 7, y + 13, x + 2, y + 8, color);
                line(g, x + 5, y + 5, x + 10, y + 10, color);
            }
            case PICK -> {
                line(g, x + 3, y + 12, x + 10, y + 5, color); line(g, x + 5, y + 14, x + 12, y + 7, color);
                line(g, x + 8, y + 3, x + 14, y + 9, color); line(g, x + 10, y + 5, x + 13, y + 2, color);
                g.fill(x + 2, y + 13, x + 5, y + 15, color);
            }
            case FILL -> {
                line(g, x + 2, y + 8, x + 7, y + 3, color); line(g, x + 7, y + 3, x + 12, y + 8, color);
                line(g, x + 12, y + 8, x + 7, y + 13, color); line(g, x + 7, y + 13, x + 2, y + 8, color);
                line(g, x + 2, y + 8, x + 12, y + 8, color); g.fill(x + 13, y + 11, x + 15, y + 14, color);
            }
            case DELETE -> {
                g.fill(x + 3, y + 3, x + 13, y + 5, color); g.fill(x + 6, y + 1, x + 10, y + 3, color);
                TEPanel.outline(g, x + 4, y + 5, 8, 10, color);
                g.fill(x + 6, y + 7, x + 7, y + 13, color); g.fill(x + 9, y + 7, x + 10, y + 13, color);
            }
            case OPEN -> {
                TEPanel.outline(g, x + 2, y + 5, 12, 9, color); TEPanel.outline(g, x + 2, y + 3, 6, 4, color);
                g.fill(x + 3, y + 8, x + 13, y + 9, color);
            }
            case SAVE -> {
                TEPanel.outline(g, x + 2, y + 2, 12, 12, color); TEPanel.outline(g, x + 5, y + 2, 6, 4, color);
                TEPanel.outline(g, x + 5, y + 9, 6, 5, color);
            }
            default -> icon.render(g, x, y);
        }
    }
    private static void line(GuiGraphics g, int x, int y, int endX, int endY, int color) {
        int dx = Math.abs(endX - x), sx = x < endX ? 1 : -1, dy = -Math.abs(endY - y), sy = y < endY ? 1 : -1, error = dx + dy;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == endX && y == endY) break;
            int e = 2 * error;
            if (e >= dy) { error += dy; x += sx; }
            if (e <= dx) { error += dx; y += sy; }
        }
    }
}
