package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import com.destan.trafficengine.client.gui.PatternCatalog;
import com.destan.trafficengine.client.gui.PatternCatalog.Category;
import com.destan.trafficengine.client.gui.PatternCatalog.Pattern;
import com.destan.trafficengine.client.gui.PatternFavorites;
import com.destan.trafficengine.client.gui.RoadAreaPreview;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.TEChrome;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.components.TESearchBox;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.data.RoadPatternNumbers;
import com.destan.trafficengine.item.RoadAreaGeometry;
import com.destan.trafficengine.network.packets.cts.PaintBrushPacket;
import com.destan.trafficengine.network.packets.cts.PaintBrushUndoPacket;
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
    private final String customCaption = Component.translatable("gui.trafficengine.stencil.custom").getString();
    private ResourceLocation previewTexture;
    private final int tint;
    private int patternId;
    private boolean saved, customEnabled;
    private com.destan.trafficengine.data.PixelPattern customPattern = com.destan.trafficengine.data.PixelPattern.EMPTY;
    boolean batchPainting;
    int lineStyle, paintWidth, dashGap, linePosition, areaStyle, areaShape, pathShape;
    private RoadAreaGeometry.Result areaPreview;
    private final net.minecraft.world.InteractionHand brushHand;
    private Category category = Category.ALL;
    private String query = "";
    private int rowOffset, columns, rows, gridX, gridY, gridWidth, gridHeight, previewX, previewHeight;
    private static final int CELL = 29;
    private double uiScale = 0.9;
    private TESearchBox search;
    private TEButton favoriteButton,areaButton;
    private List<FormattedCharSequence> favoriteLines = List.of();
    private int favoriteCaptionColor;
    private List<Pattern> filtered = List.of();
    private final List<PatternButton> patternButtons = new ArrayList<>();
    private final EnumMap<Category, TEButton> tabs = new EnumMap<>(Category.class);
    private boolean draggingScrollbar;

    public PaintBrushScreen(int patternId, PaintColor color, int lineStyle, boolean batch, int paintWidth, int dashGap, int linePosition, int areaStyle, int areaShape, net.minecraft.world.InteractionHand hand) {
        this(patternId,color,lineStyle,batch,paintWidth,dashGap,linePosition,areaStyle,areaShape,0,hand);
    }

    public PaintBrushScreen(int patternId, PaintColor color, int lineStyle, boolean batch, int paintWidth, int dashGap, int linePosition, int areaStyle, int areaShape, int pathShape, net.minecraft.world.InteractionHand hand) {
        super(Component.translatable("gui.trafficengine.paint_brush.title"));
        this.patternId = patternId;
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            var brushTag = com.destan.trafficengine.util.ItemData.getOrCreate(net.minecraft.client.Minecraft.getInstance().player.getItemInHand(hand));
            try { category = Category.valueOf(brushTag.getString(com.destan.trafficengine.item.BrushItem.NBT_CATEGORY)); }
            catch (IllegalArgumentException ignored) { category = Category.ALL; }
            customEnabled = brushTag.getBoolean("custom_road_enabled");
            customPattern = com.destan.trafficengine.data.PixelPattern.of(brushTag.getLongArray("custom_road_pattern"));
            customEnabled = customEnabled && !customPattern.isEmpty();
        }
        this.color = color;
        this.lineStyle = lineStyle; this.batchPainting = batch; this.paintWidth = com.destan.trafficengine.item.RoadPaintPlan.normalizeWidth(paintWidth); this.brushHand = hand;
        this.dashGap = dashGap == 4 ? 4 : 2;
        this.linePosition = Math.max(0, Math.min(2, linePosition));
        this.pathShape=com.destan.trafficengine.item.RoadCurveGeometry.normalize(pathShape);
        this.linePosition=com.destan.trafficengine.item.RoadPaintPlan.normalizePosition(batch,this.paintWidth,this.pathShape,this.linePosition);
        this.areaStyle=Math.max(0,Math.min(3,areaStyle));this.areaShape=Math.max(0,Math.min(2,areaShape));
        // This is the same diffuse tint used by the old preview.
        this.tint = color.getTextureColor().getAsARGB();
    }

    @Override protected void init() {
        int screenWidth = width, screenHeight = height;
        uiScale=Math.min(0.9,Math.min(width/516.0,height/305.0));
        width = (int)(width / uiScale);
        height = (int)(height / uiScale);
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
        rows = Math.max(1, (windowHeight - 136) / CELL);
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
        Component optionsLabel = Component.translatable("gui.trafficengine.paint_brush.options");
        Component areaLabel = Component.translatable("gui.trafficengine.paint_brush.area.title");
        int optionsWidth = Math.max(100, (int)Math.ceil(font.width(optionsLabel) / .9) + 18);
        int areaWidth = Math.max(90, font.width(areaLabel) + 24);
        addRenderableWidget(new TEButton(left + 12, top + windowHeight - 30, optionsWidth, 20,
            optionsLabel, b -> minecraft.setScreen(new PaintBrushOptionsScreen(clearCustom()))));
        areaButton=addRenderableWidget(new TEButton(left + 20 + optionsWidth,top+windowHeight-30,areaWidth,20,
            Component.translatable("gui.trafficengine.paint_brush.area.title"),b->minecraft.setScreen(new PaintBrushAreaScreen(clearCustom())))
            .selected(areaStyle>0));
        int customWidth = Math.max(64, font.width(Component.translatable("gui.trafficengine.stencil.custom")) + 16);
        addRenderableWidget(new TEButton(left + windowWidth - 35 - customWidth, top + 8, customWidth, 19,
            Component.translatable("gui.trafficengine.stencil.custom"), b -> minecraft.setScreen(new PixelPatternScreen(this, customPattern, value -> {
                customPattern = value; customEnabled = !value.isEmpty(); areaStyle = 0; pathShape = 0; lineStyle = -1; batchPainting = false;
            }))));
        int undoWidth = Math.max(60, font.width(Component.translatable("gui.trafficengine.paint_brush.undo")) + 16);
        TEButton undoButton = addRenderableWidget(new TEButton(left + windowWidth - 118 - undoWidth, top + windowHeight - 30, undoWidth, 20,
            Component.translatable("gui.trafficengine.paint_brush.undo"),b -> {
                onClose();
                ModNetworkManager.UNDO_PAINT_BRUSH.send(NetworkDirection.C2S,new PaintBrushUndoPacket(brushHand));
            }));
        undoButton.setTooltip(Tooltip.create(Component.translatable("gui.trafficengine.paint_brush.undo.tooltip")));
        addRenderableWidget(new TEButton(left + windowWidth - 112, top + windowHeight - 30, 100, 20,
            Component.translatable("gui.done"), b -> onClose()).primary());
        int favoriteY = previewHeight >= 125 ? gridY + previewHeight - 34 : top + windowHeight - 36;
        int favoriteX = previewHeight >= 125 ? previewX + 6 : left + 12;
        favoriteButton = addRenderableWidget(new TEButton(favoriteX, favoriteY, 100, 26, Component.empty(), b -> {
            if (customEnabled) PatternFavorites.toggle(customPattern); else PatternFavorites.toggle(patternId);
            updateFavoriteButton();
            if (category == Category.FAVORITES) filter();
        }) {
            @Override protected void renderLabel(GuiGraphics g, int color) {
                float scale = TEText.layoutScale(g);
                int lineHeight = Math.round(font.lineHeight * scale);
                float y = TEText.centeredLinesY(g,font,favoriteLines,getY(),getHeight());
                for (FormattedCharSequence line : favoriteLines) {
                    drawFavoriteLine(g, line, getX() + (getWidth() - TEText.width(font,line) * scale) / 2F, y,
                        active ? favoriteCaptionColor : TEColors.MUTED);
                    y += lineHeight;
                }
            }
        });
        updateFavoriteButton();
        filter();
    }

    private void drawFavoriteLine(GuiGraphics graphics, FormattedCharSequence line, float x, float y, int color) {
        TEText.drawString(graphics, font, line, x, y, color, false);
        // A small extra stroke keeps this action emphasized, just below the old 0.8 weight.
        var pose = graphics.pose().last().pose();
        float previousX = pose.m30();
        pose.m30(previousX + .7F * pose.m00());
        try { TEText.drawString(graphics, font, line, x, y, color, false); }
        finally { pose.m30(previousX); }
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) return;
        boolean favorite = customEnabled ? PatternFavorites.contains(customPattern) : PatternFavorites.contains(patternId);
        favoriteButton.setMessage(Component.translatable(favorite
            ? "gui.trafficengine.patterns.remove_favorite" : "gui.trafficengine.patterns.add_favorite").withStyle(ChatFormatting.BOLD));
        // Cache whole regular lines when the selection changes, not glyph Components every frame.
        favoriteLines = font.split(favoriteButton.getMessage().copy().withStyle(style -> style.withBold(false)), favoriteButton.getWidth() - 8);
        favoriteCaptionColor = favorite ? 0xFFFF7777 : 0xFF8DEA9D;
        favoriteButton.setSelected(favorite);
        favoriteButton.active = customEnabled ? !customPattern.isEmpty() && (favorite || PatternFavorites.canAddCustom())
            : areaStyle==0 && lineStyle < 0 && patternId >= 0 && patternId < 318;
        favoriteButton.visible = customEnabled || favoriteButton.active;
        favoriteButton.setTooltip(!favoriteButton.active && customEnabled ? Tooltip.create(Component.translatable("gui.trafficengine.patterns.custom_limit")) : null);
        if (!favoriteButton.visible && getFocused()==favoriteButton) setFocused(search);
    }
    private void filter() {
        List<Pattern> choices = new ArrayList<>(patterns);
        if (category == Category.ALL || category == Category.FAVORITES) {
            var custom = PatternFavorites.customPatterns();
            for (int i = 0; i < custom.size(); i++) choices.add(PatternCatalog.custom(i, custom.get(i)));
        }
        if (category == Category.FAVORITES) {
            filtered = choices.stream().filter(p -> (!p.custom().isEmpty() || PatternFavorites.contains(p.id())) && p.matches(query)).toList();
        } else {
            filtered = choices.stream().filter(p -> (category == Category.ALL || p.category() == category)
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
        areaPreview=areaStyle>0?RoadAreaPreview.sample(areaShape,areaStyle)
            :batchPainting && pathShape>0?RoadAreaPreview.curveSample(pathShape,paintWidth,lineStyle,dashGap,linePosition):null;
        previewTexture = areaPreview!=null || lineStyle>=0 ? null : selected==null ? null : selected.texture();
        selectionLabel = areaStyle==0 && batchPainting && pathShape>0?Component.translatable("gui.trafficengine.paint_brush.path."+pathShape).getString():areaStyle>0?font.plainSubstrByWidth(Component.translatable("gui.trafficengine.paint_brush.area.style."+areaStyle).getString(),96)
            :lineStyle >= 0 ? font.plainSubstrByWidth(Component.translatable("gui.trafficengine.paint_brush.connection." + (lineStyle + 1)).getString(), 96) : "#" + RoadPatternNumbers.displayNumber(id);
        updateFavoriteButton();
        if(areaButton!=null)areaButton.setSelected(areaStyle>0);
        patternButtons.forEach(b -> b.setSelected(isChosen(b.pattern)));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (mouseX >= gridX && mouseX <= previewX - 8 && mouseY >= gridY && mouseY < gridY + gridHeight) {
            setRowOffset(Math.max(0, Math.min(maxOffset(), rowOffset - (int)Math.signum(delta))));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, delta);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX /= uiScale;
        mouseY /= uiScale;
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
        mouseX /= uiScale;
        mouseY /= uiScale;
        dx /= uiScale;
        dy /= uiScale;
        if (draggingScrollbar) { scrollTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / uiScale, y / uiScale); }


    @Override public void onClose() {
        // Match the old screen: closing with Escape or Done applies the selection.
        if (!saved) {
            saved = true;
            ModNetworkManager.UPDATE_PAINT_BRUSH.send(NetworkDirection.toServer(), new PaintBrushPacket(patternId, lineStyle, batchPainting, paintWidth, dashGap, linePosition, areaStyle, areaShape, pathShape, brushHand).withPattern(customPattern, customEnabled).withCategory(category.name()));
        }
        super.onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, TEColors.OVERLAY);
        g.pose().pushPose();
        g.pose().scale((float)uiScale, (float)uiScale, 1);
        TEChrome.draw(g, font, left, top, windowWidth, windowHeight, title);
        TEPanel.draw(g, gridX - 3, gridY - 3, gridWidth + 13, previewHeight + 6);
        TEPanel.draw(g, previewX, gridY - 3, 112, previewHeight + 6);
        if (filtered.isEmpty()) {
            TEText.drawWordWrap(g, font, emptyLabel,
                gridX + 9, gridY + 12, gridWidth - 18, TEColors.MUTED);
        }
        if (maxOffset() > 0) {
            int thumb = Math.max(16, gridHeight * rows / (maxOffset() + rows));
            int thumbY = gridY + (gridHeight - thumb) * rowOffset / maxOffset();
            g.fill(gridX + gridWidth + 3, gridY, gridX + gridWidth + 7, gridY + gridHeight, TEColors.FIELD);
            g.fill(gridX + gridWidth + 3, thumbY, gridX + gridWidth + 7, thumbY + thumb, TEColors.ACCENT);
        }
        TEText.drawString(g, font, previewLabel, previewX + 8, gridY + 5, TEColors.MUTED, false);
        int size = Math.max(24, Math.min(lineStyle>=0 || areaPreview!=null?96:66, previewHeight - (lineStyle>=0 || areaPreview!=null?55:78)));
        int px = previewX + (112 - size) / 2;
        g.fill(px, gridY + 19, px + size, gridY + 19 + size, TEColors.FIELD);
        if (customEnabled) {
            int drawSize = size - 8;
            for (var rect : customPattern.rectangles()) g.fill(px + 4 + rect.x() * drawSize / 16, gridY + 23 + rect.y() * drawSize / 16,
                px + 4 + (rect.x() + rect.width()) * drawSize / 16, gridY + 23 + (rect.y() + rect.height()) * drawSize / 16, tint);
        } else if(areaPreview!=null)RoadAreaPreview.draw(g,areaPreview,px+4,gridY+23,size-8,size-8,tint);
        else if(lineStyle>=0) com.destan.trafficengine.client.gui.RoadLinePreview.straight(g,px+4,gridY+23,size-8,size-8,lineStyle,batchPainting?paintWidth:1,dashGap,linePosition,true,tint);
        if (!customEnabled && previewTexture != null) drawPattern(g, previewTexture, px + 4, gridY + 23, size - 8);
        int detailsY = gridY + 22 + size;
        TEText.drawString(g, font, !customEnabled ? selectionLabel : customCaption, previewX + 8, detailsY, TEColors.ACCENT, false);
        TEText.drawString(g, font, colorLabel, previewX + 8,
            detailsY + 11, TEColors.TEXT, false);
        super.render(g, (int)(mouseX / uiScale), (int)(mouseY / uiScale), partialTick);
        g.pose().popPose();
    }

    private void drawPattern(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        RenderSystem.enableBlend();
        g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        g.blit(texture, x, y, size, size, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
    PaintColor paintColor() { return color; }
    int paintTint() { return tint; }

    private final class PatternButton extends TEButton {
        private Pattern pattern;
        private ResourceLocation texture;
        PatternButton(int x, int y, Pattern pattern) {
            super(x, y, CELL - 2, CELL - 2, Component.empty(), b -> selectPattern(((PatternButton)b).pattern));
            setPattern(pattern);
        }
        private void setPattern(Pattern pattern) {
            if (this.pattern != pattern) {
                this.pattern = pattern;
                texture = pattern.custom().isEmpty() ? pattern.texture() : null;
                setTooltip(Tooltip.create(pattern.custom().isEmpty() ? pattern.name().copy().append(" (#" + pattern.displayNumber() + ")") : pattern.name()));
            }
            setSelected(isChosen(pattern));
        }
        @Override protected void renderLabel(GuiGraphics g, int color) {
            // Keep road marking contrast even on the amber selection border.
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, TEColors.FIELD);
            if (texture != null) drawPattern(g, texture, getX() + 4, getY() + 4, CELL - 10);
            else for (var r : pattern.custom().rectangles()) {
                int size = CELL - 10;
                g.fill(getX() + 4 + r.x() * size / 16, getY() + 4 + r.y() * size / 16,
                    getX() + 4 + (r.x() + r.width()) * size / 16, getY() + 4 + (r.y() + r.height()) * size / 16, tint);
            }
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, pattern.custom().isEmpty() ? pattern.name().copy().append(" #" + pattern.displayNumber()) : pattern.name());
            output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
        }
    }
    private boolean isChosen(Pattern pattern) {
        return customEnabled ? !pattern.custom().isEmpty() && pattern.custom().equals(customPattern)
            : areaStyle == 0 && pattern.custom().isEmpty() && pattern.id() == RoadPatternNumbers.canonicalId(patternId);
    }
    private void selectPattern(Pattern pattern) {
        customEnabled = !pattern.custom().isEmpty();
        if (customEnabled) { customPattern = pattern.custom(); patternId = 0; }
        else patternId = pattern.id();
        areaStyle = 0; pathShape = 0; lineStyle = -1; batchPainting = false;
        select(patternId);
    }
    private PaintBrushScreen clearCustom() { customEnabled = false; return this; }
}
