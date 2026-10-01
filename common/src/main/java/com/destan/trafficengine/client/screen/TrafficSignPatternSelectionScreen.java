package com.destan.trafficengine.client.screen;

import java.util.*;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.client.gui.*;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.*;
import com.destan.trafficengine.item.*;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.packets.cts.*;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.item.ItemStack;

public class TrafficSignPatternSelectionScreen extends TrafficEngineScreen {
    private final ItemStack stack;
    private final boolean creative;
    private final SignLibrary library = new SignLibrary();
    private final List<AbstractWidget> cells = new ArrayList<>();
    private final Map<String, TrafficSignClientTexture> owned = new HashMap<>();
    private TrafficSignShape category;
    private List<SignLibrary.Entry> entries = List.of();
    private int firstRow, columns, rows, cellSize, gridY, gridWidth;
    private boolean sent;
    public TrafficSignPatternSelectionScreen(ItemStack stack) {
        super(Component.translatable("gui.trafficengine.patternselection.title"));
        this.stack = stack;
        creative = stack.getItem() instanceof CreativePatternCatalogueItem;
        if (creative && !CreativePatternCatalogueItem.getSelectedTab(stack).equals("CUSTOM")) {
            try { category = TrafficSignShape.valueOf(CreativePatternCatalogueItem.getSelectedTab(stack)); }
            catch (IllegalArgumentException e) { category = TrafficSignShape.CIRCLE; }
        }
    }
    @Override protected void init() {
        cells.clear();
        layoutWindow(400, 288);
        addCloseButton();
        int x = left + 12;
        if (creative) {
            for (TrafficSignShape shape : SignLibrary.CATEGORIES) {
                final int bx = x;
                TEButton b = addRenderableWidget(new TEButton(bx, top + 44, 34, 23, Component.empty(), button -> { category = shape; firstRow = 0; rebuild(); }) {
                    @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) {
                        selected(category == shape); super.renderWidget(g, mx, my, dt);
                        g.blit(shape.getIconResourceLocation(), getX() + 9, getY() + 3, 16, 16, 0, 0, 16, 16, 16, 16);
                    }
                });
                b.setTooltip(Tooltip.create(Component.translatable(shape.getTranslationKey())));
                x += 38;
            }
            addRenderableWidget(new TEButton(x, top + 44, windowWidth - (x - left) - 12, 23, Component.translatable("gui.trafficengine.patternselection.custom"), b -> { category = null; firstRow = 0; rebuild(); }) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) { selected(category == null); super.renderWidget(g, mx, my, dt); }
            });
        }
        gridY = top + (creative ? 76 : 45);
        gridWidth = windowWidth - 124;
        cellSize = 29;
        columns = Math.max(1, (gridWidth - 4) / cellSize);
        rows = Math.max(1, (windowHeight - (gridY - top) - 42) / cellSize);
        addRenderableWidget(new TEButton(left + windowWidth - 104, top + windowHeight - 29, 92, 20, CommonComponents.GUI_DONE, b -> onClose()).primary());
        rebuild();
    }
    private void rebuild() {
        cells.forEach(this::removeWidget); cells.clear();
        entries = category == null ? List.of() : library.category(category);
        int count = category == null ? PatternCatalogueItem.getStoredPatternCount(stack) : entries.size();
        firstRow = Math.max(0, Math.min(firstRow, (count + columns - 1) / columns - rows));
        Set<String> needed = new HashSet<>();
        var selected = ((PatternCatalogueItem)stack.getItem()).getSelectedImageData(stack);
        if (selected != null) needed.add(selected.getTextureId());
        for (int i = firstRow * columns; i < Math.min(count, (firstRow + rows) * columns); i++) {
            final int index = i;
            NamedTrafficSignTextureReference ref = category == null ? PatternCatalogueItem.getPatternAt(stack, i) : entries.get(i).reference();
            needed.add(ref.getTextureId());
            int cx = left + 14 + (i % columns) * cellSize, cy = gridY + 2 + (i / columns - firstRow) * cellSize;
            TEButton button = addRenderableWidget(new TEButton(cx, cy, cellSize - 2, cellSize - 2, Component.empty(), b -> {
                if (category == null) { PatternCatalogueItem.setSelectedIndex(stack, index); if (creative) CreativePatternCatalogueItem.clearCustomImage(stack); }
                else {
                    CreativePatternCatalogueItem.setCustomImage(stack, ref);
                    PatternCatalogueItem.setSelectedIndex(stack, -1);
                }
                rebuild();
            }) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) {
                    var current = ((PatternCatalogueItem)stack.getItem()).getSelectedImageData(stack);
                    selected(current != null && current.getTextureId().equals(ref.getTextureId()));
                    super.renderWidget(g, mx, my, dt);
                    drawTexture(g, ref, getX() + 3, getY() + 3, cellSize - 8);
                }
            });
            button.setTooltip(Tooltip.create(Component.literal(ref.getName().isEmpty() ? "#" + (i + 1) : ref.getName())));
            cells.add(button);
        }
        owned.entrySet().removeIf(e -> { if (!needed.contains(e.getKey())) { e.getValue().close(); return true; } return false; });
        for (String id : needed) owned.computeIfAbsent(id, n -> TrafficSignClientTexture.load(n, false, null));
    }
    private void drawTexture(GuiGraphics g, NamedTrafficSignTextureReference ref, int x, int y, int size) {
        TrafficSignClientTexture texture = owned.get(ref.getTextureId());
        if (texture == null || !texture.isFullyLoaded()) return;
        int w = texture.getRawData().getWidth(), h = texture.getRawData().getHeight();
        if (w > 0 && h > 0) g.blit(texture.getTextureLocation(), x, y, size, size, 0, 0, w, h, w, h);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) { firstRow += delta > 0 ? -1 : 1; rebuild(); return true; }
    @Override public void onClose() {
        if (!sent) {
            sent = true;
            if (!creative || !CreativePatternCatalogueItem.shouldUseCustomPattern(stack)) ModNetworkManager.UPDATE_PATTERN_CATALOG_INDEX.send(NetworkDirection.toServer(), new PatternCatalogueIndexPacket(PatternCatalogueItem.getSelectedIndex(stack)));
            if (creative) ModNetworkManager.UPDATE_CREATIVE_PATTERN_CATALOG_ITEM.send(NetworkDirection.toServer(), new CreativePatternCataloguePacket(CreativePatternCatalogueItem.shouldUseCustomPattern(stack) ? CreativePatternCatalogueItem.getCustomImage(stack) : null, category == null ? "CUSTOM" : category.name()));
        }
        super.onClose();
    }
    @Override public void removed() { owned.values().forEach(TrafficSignClientTexture::close); owned.clear(); }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderWindow(g);
        TEPanel.draw(g, left + 12, gridY, gridWidth, rows * cellSize + 4);
        int px = left + windowWidth - 105;
        TEPanel.draw(g, px - 6, gridY, 99, rows * cellSize + 4);
        g.drawString(font, Component.translatable("gui.trafficengine.patterns.preview"), px, gridY + 8, TEColors.MUTED, false);
        var ref = ((PatternCatalogueItem)stack.getItem()).getSelectedImageData(stack);
        if (ref != null) { drawTexture(g, ref, px + 9, gridY + 30, 64); g.drawString(font, font.plainSubstrByWidth(ref.getName(), 88), px, gridY + 113, TEColors.TEXT, false); }
        super.render(g, mx, my, tick);
    }
}
