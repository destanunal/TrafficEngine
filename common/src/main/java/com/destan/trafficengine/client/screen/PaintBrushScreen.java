package com.destan.trafficengine.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import com.destan.trafficengine.client.gui.PatternCatalog;
import com.destan.trafficengine.client.gui.PatternCatalog.Category;
import com.destan.trafficengine.client.gui.PatternCatalog.Pattern;
import com.destan.trafficengine.client.gui.PatternFavorites;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.TEChrome;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.components.TESearchBox;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.network.packets.cts.PaintBrushPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class PaintBrushScreen extends TrafficEngineScreen {
    private final PaintColor color;
    private final List<Pattern> patterns = PatternCatalog.all();
    private final Component previewLabel = Component.translatable("gui.trafficengine.patterns.preview");
    private Component emptyLabel;
    private String colorLabel, selectionLabel;
    private ResourceLocation previewTexture;
    private final int tint;
    private int patternId;
    private boolean saved;
    private Category category = PatternFavorites.getLastCategory();
    private String query = "";
    private int rowOffset, columns, rows, gridX, gridY, gridWidth, gridHeight, previewX, previewHeight;
    private static final int CELL = 29;
    private static final double UI_SCALE = 0.9;
    private TESearchBox search;
    private TEButton favoriteButton;
    private List<FormattedCharSequence> favoriteLines = List.of();
    private int favoriteCaptionColor;
    private List<Pattern> filtered = List.of();
    private final List<PatternButton> patternButtons = new ArrayList<>();
    private final EnumMap<Category, TEButton> tabs = new EnumMap<>(Category.class);
    private boolean draggingScrollbar;

    public PaintBrushScreen(int patternId, PaintColor color) {
        super(Component.translatable("gui.trafficengine.paint_brush.title"));
        this.patternId = patternId;
        this.color = color;
        // This is the same diffuse tint used by the old preview.
        this.tint = color.getTextureColor().getAsARGB();
    }

    @Override protected void init() {
        int screenWidth = width, screenHeight = height;
        width = (int)(width / UI_SCALE);
        height = (int)(height / UI_SCALE);
        initLayout();
        width = screenWidth;
        height = screenHeight;
    }
    private void initLayout() {
        layoutWindow(504, 293);
        tabs.clear();
        patternButtons.clear();
        previewX = left + windowWidth - 124;
        gridX = left + 12;
        gridY = top + 99;
        columns = Math.max(1, (previewX - gridX - 16) / CELL);
        rows = Math.max(1, (windowHeight - (windowHeight >= 266 ? 112 : 148)) / CELL);
        gridWidth = columns * CELL;
        gridHeight = rows * CELL;
        previewHeight = windowHeight - 141;
        colorLabel = font.plainSubstrByWidth(color.getValueTranslation().getString(), 96);
        select(patternId);
        search = addRenderableWidget(new TESearchBox(font, left + 17, top + 47, windowWidth - 34, 12,
            Component.translatable("gui.trafficengine.patterns.search")));
        search.setValue(query);
        search.setResponder(value -> { query = value; rowOffset = 0; filter(); });
        Category[] categories = Category.values();
        int tabWidth = (windowWidth - 24) / categories.length;
        for (int i = 0; i < categories.length; i++) {
            Category tab = categories[i];
            TEButton button = addRenderableWidget(new TEButton(left + 12 + i * tabWidth, top + 71,
                tabWidth - 2, 20, tab.title(), b -> { category = tab; rowOffset = 0; filter(); }));
            tabs.put(tab, button);
        }
        addCloseButton();
        addRenderableWidget(new TEButton(left + windowWidth - 112, top + windowHeight - 30, 100, 20,
            Component.translatable("gui.done"), b -> onClose()) {
            @Override protected void renderLabel(GuiGraphics g, int foreground) {
                // Keep the caption at the same pixel scale as the other menus.
                float scale = (float)UI_SCALE;
                int x = Math.round((getX() + getWidth() / 2.0F) * scale) - font.width(getMessage()) / 2;
                int y = Math.round((getY() + getHeight() / 2.0F) * scale) - (font.lineHeight + 1) / 2;
                g.pose().pushPose();
                g.pose().scale(1.0F / scale, 1.0F / scale, 1);
                g.drawString(font, getMessage(), x, y, foreground, false);
                g.pose().popPose();
            }
        }.primary());
        int favoriteY = previewHeight >= 125 ? gridY + previewHeight - 34 : top + windowHeight - 36;
        int favoriteX = previewHeight >= 125 ? previewX + 6 : left + 12;
        favoriteButton = addRenderableWidget(new TEButton(favoriteX, favoriteY, 100, 26, Component.empty(), b -> {
            PatternFavorites.toggle(patternId);
            updateFavoriteButton();
            if (category == Category.FAVORITES) filter();
        }) {
            @Override protected void renderLabel(GuiGraphics g, int color) {
                int y = getY() + (getHeight() - favoriteLines.size() * font.lineHeight) / 2;
                for (FormattedCharSequence line : favoriteLines) {
                    g.drawString(font, line, getX() + (getWidth() - font.width(line)) / 2, y,
                        active ? favoriteCaptionColor : TEColors.MUTED, false);
                    y += font.lineHeight;
                }
            }
        });
        updateFavoriteButton();
        filter();
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) return;
        boolean favorite = PatternFavorites.contains(patternId);
        favoriteButton.setMessage(Component.translatable(favorite
            ? "gui.trafficengine.patterns.remove_favorite" : "gui.trafficengine.patterns.add_favorite").withStyle(ChatFormatting.BOLD));
        favoriteLines = font.split(favoriteButton.getMessage(), favoriteButton.getWidth() - 8);
        favoriteCaptionColor = favorite ? 0xFFFF7777 : 0xFF8DEA9D;
        favoriteButton.setSelected(favorite);
        favoriteButton.active = patternId >= 0 && patternId < 318;
    }
    private void filter() {
        if (category == Category.FAVORITES) {
            filtered = patterns.stream().filter(p -> PatternFavorites.contains(p.id()) && p.matches(query)).toList();
        } else {
            filtered = patterns.stream().filter(p -> (category == Category.ALL || p.category() == category)
                && p.matches(query)).toList();
        }
        emptyLabel = Component.translatable(category == Category.FAVORITES
            ? "gui.trafficengine.patterns.no_favorites" : "gui.trafficengine.patterns.no_results");
        tabs.forEach((tab, button) -> button.setSelected(tab == category));
        rowOffset = Math.min(rowOffset, maxOffset());
        rebuildGrid();
    }

    private int maxOffset() { return Math.max(0, (filtered.size() + columns - 1) / columns - rows); }
    private void rebuildGrid() {
        int start = rowOffset * columns;
        int count = Math.min(filtered.size() - start, columns * rows);
        while (patternButtons.size() > count) {
            PatternButton button = patternButtons.remove(patternButtons.size() - 1);
            if (getFocused() == button) setFocused(null);
            removeWidget(button);
        }
        for (int cell = 0; cell < count; cell++) {
            Pattern pattern = filtered.get(start + cell);
            if (cell < patternButtons.size()) patternButtons.get(cell).setPattern(pattern);
            else patternButtons.add(addRenderableWidget(new PatternButton(gridX + cell % columns * CELL,
                gridY + cell / columns * CELL, pattern)));
        }
    }

    private void select(int id) {
        patternId = id;
        Pattern selected = PatternCatalog.byId(id);
        previewTexture = selected == null ? null : selected.texture();
        selectionLabel = "#" + id;
        updateFavoriteButton();
        patternButtons.forEach(b -> b.setSelected(b.pattern.id() == patternId));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
        mouseX /= UI_SCALE;
        mouseY /= UI_SCALE;
        if (mouseX >= gridX && mouseX <= previewX - 8 && mouseY >= gridY && mouseY < gridY + gridHeight) {
            setRowOffset(Math.max(0, Math.min(maxOffset(), rowOffset - (int)Math.signum(delta))));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, delta);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX /= UI_SCALE;
        mouseY /= UI_SCALE;
        if (button == 0 && maxOffset() > 0 && mouseX >= gridX + gridWidth + 2 && mouseX < gridX + gridWidth + 9
            && mouseY >= gridY && mouseY < gridY + gridHeight) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scrollTo(double mouseY) {
        int thumb = Math.max(16, gridHeight * rows / (maxOffset() + rows));
        setRowOffset(Math.max(0, Math.min(maxOffset(), (int)Math.round((mouseY - gridY - thumb / 2.0)
            / Math.max(1, gridHeight - thumb) * maxOffset()))));
    }
    private void setRowOffset(int offset) {
        if (offset == rowOffset) return;
        rowOffset = offset;
        rebuildGrid();
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        mouseX /= UI_SCALE;
        mouseY /= UI_SCALE;
        dx /= UI_SCALE;
        dy /= UI_SCALE;
        if (draggingScrollbar) { scrollTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX / UI_SCALE, mouseY / UI_SCALE, button);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / UI_SCALE, y / UI_SCALE); }


    @Override public void onClose() {
        PatternFavorites.setLastCategory(category);
        // Match the old screen: closing with Escape or Done applies the selection.
        if (!saved) {
            saved = true;
            ModNetworkManager.UPDATE_PAINT_BRUSH.send(NetworkDirection.toServer(), new PaintBrushPacket(patternId));
        }
        super.onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x801A2028);
        g.pose().pushPose();
        g.pose().scale((float)UI_SCALE, (float)UI_SCALE, 1);
        TEChrome.draw(g, font, left, top, windowWidth, windowHeight, title);
        TEPanel.draw(g, gridX - 3, gridY - 3, gridWidth + 13, gridHeight + 6);
        TEPanel.draw(g, previewX, gridY - 3, 112, previewHeight + 6);
        if (filtered.isEmpty()) {
            g.drawWordWrap(font, emptyLabel,
                gridX + 9, gridY + 12, gridWidth - 18, TEColors.MUTED);
        }
        if (maxOffset() > 0) {
            int thumb = Math.max(16, gridHeight * rows / (maxOffset() + rows));
            int thumbY = gridY + (gridHeight - thumb) * rowOffset / maxOffset();
            g.fill(gridX + gridWidth + 3, gridY, gridX + gridWidth + 7, gridY + gridHeight, TEColors.FIELD);
            g.fill(gridX + gridWidth + 3, thumbY, gridX + gridWidth + 7, thumbY + thumb, TEColors.ACCENT);
        }
        g.drawString(font, previewLabel, previewX + 8, gridY + 5, TEColors.MUTED, false);
        int size = Math.max(24, Math.min(66, previewHeight - 78));
        int px = previewX + (112 - size) / 2;
        g.fill(px, gridY + 19, px + size, gridY + 19 + size, TEColors.FIELD);
        if (previewTexture != null) drawPattern(g, previewTexture, px + 4, gridY + 23, size - 8);
        int detailsY = gridY + 22 + size;
        g.drawString(font, selectionLabel, previewX + 8, detailsY, TEColors.ACCENT, false);
        g.drawString(font, colorLabel, previewX + 8,
            detailsY + 11, TEColors.TEXT, false);
        super.render(g, (int)(mouseX / UI_SCALE), (int)(mouseY / UI_SCALE), partialTick);
        g.pose().popPose();
    }

    private void drawPattern(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        RenderSystem.enableBlend();
        g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        g.blit(texture, x, y, size, size, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private final class PatternButton extends TEButton {
        private Pattern pattern;
        private ResourceLocation texture;
        PatternButton(int x, int y, Pattern pattern) {
            super(x, y, CELL - 2, CELL - 2, Component.empty(), b -> select(((PatternButton)b).pattern.id()));
            setPattern(pattern);
        }
        private void setPattern(Pattern pattern) {
            if (this.pattern != pattern) {
                this.pattern = pattern;
                texture = pattern.texture();
                setTooltip(Tooltip.create(pattern.name().copy().append(" (#" + pattern.id() + ")")));
            }
            setSelected(pattern.id() == patternId);
        }
        @Override protected void renderLabel(GuiGraphics g, int color) {
            // Keep road marking contrast even on the amber selection border.
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, TEColors.FIELD);
            drawPattern(g, texture, getX() + 4, getY() + 4, CELL - 10);
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, pattern.name().copy().append(" #" + pattern.id()));
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        }
    }
}
