package com.destan.trafficengine.client.screen;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.client.gui.*;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.client.screen.menu.TrafficSignWorkbenchMenu;
import com.destan.trafficengine.data.*;
import com.destan.trafficengine.item.*;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.packets.cts.*;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Pages share one container screen so editing never closes or drops the inventory. */
public class TrafficSignWorkbenchGui extends AbstractContainerScreen<TrafficSignWorkbenchMenu> {
    private enum Page { MAIN, CREATE, EDIT, IMPORT, COLOR, DELETE, DISCARD }
    private Page page = Page.MAIN;
    private double guiScale = 1;
    private final SignLibrary library = new SignLibrary();
    private NamedTrafficSignTextureReference selected;
    private TrafficSignClientTexture preview;
    private ItemStack lastCatalogue = ItemStack.EMPTY;
    private static final int CANVAS_X = 110, CANVAS_Y = 86, CANVAS_SIZE = 128;
    private static final int[] COLOR_PRESETS = {0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFFD740, 0xFF43A047, 0xFF2196F3, 0xFF9C27B0, 0xFFFF9800};
    private TEColorPicker colorPicker;
    private boolean updatingHex;
    private int[] textureSource;
    private String colorCaption = "#000000";
    private TrafficSignShape shape;
    private int editIndex = -1, tool, color = 0xFF000000, importRow, lastPixel = -1;
    private final int[] pixels = new int[1024];
    private int[] imported;
    private String name = "", importError;
    private boolean dirty, busy;
    private EditBox nameBox, hexBox;
    private DynamicTexture canvasTexture;
    private ResourceLocation canvasLocation;
    private final List<AbstractWidget> importButtons = new ArrayList<>();
    public TrafficSignWorkbenchGui(TrafficSignWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.trafficengine.trafficsignworkbench.title"));
        imageWidth = 360;
        imageHeight = 306;
        inventoryLabelX = 71;
        inventoryLabelY = 226;
        titleLabelY = -100;
    }
    private static Component text(String key) { return Component.translatable("gui.trafficengine.trafficsignworkbench." + key); }
    @Override protected void init() {
        guiScale = Math.min(1, Math.min((width - 12D) / imageWidth, (height - 12D) / imageHeight));
        int screenWidth = width, screenHeight = height;
        width = (int)(width / guiScale); height = (int)(height / guiScale);
        super.init(); buildPage();
        width = screenWidth; height = screenHeight;
    }
    private void go(Page target) { if (minecraft.screen != this) return; if (nameBox != null) name = nameBox.getValue(); page = target; buildPage(); }
    private TEButton button(int x, int y, int w, Component caption, Runnable action) {
        return addRenderableWidget(new TEButton(leftPos + x, topPos + y, w, 20, caption, b -> { if (!busy) action.run(); }));
    }
    private TEButton icon(int x, int y, ModGuiIcons icon, Component caption, Runnable action) {
        TEButton b = addRenderableWidget(new TEButton(leftPos + x, topPos + y, 24, 20, caption, a -> { if (!busy) action.run(); }) {
            @Override protected int selectedBackground() { return TEColors.SELECTED; }
            @Override protected int selectedBorder() { return TEColors.SELECTION_BORDER; }
            @Override protected void renderLabel(GuiGraphics g, int ignored) {
                int foreground = !active ? TEColors.MUTED : icon == ModGuiIcons.CANCEL || icon == ModGuiIcons.DISCARD_FILE
                    ? 0xFFFF6B6B : icon == ModGuiIcons.CHECK ? 0xFF65E58B : TEColors.TEXT;
                TEWorkbenchIcons.draw(g, icon, getX() + (getWidth() - 16) / 2, getY() + 2, foreground);
            }
        });
        b.setTooltip(Tooltip.create(caption)); return b;
    }
    private boolean cataloguePresent() { return menu.patternSlot.getItem().getItem() instanceof PatternCatalogueItem; }
    private void buildPage() {
        clearWidgets(); importButtons.clear(); nameBox = null; hexBox = null; colorPicker = null;
        icon(325, 8, ModGuiIcons.CANCEL, CommonComponents.GUI_CANCEL, this::onClose);
        if (!cataloguePresent()) return;
        switch (page) {
            case MAIN -> {
                updatePreview();
                icon(26, 90, ModGuiIcons.ADD, text("menu.add"), () -> go(Page.CREATE)).active = PatternCatalogueItem.getStoredPatternCount(menu.patternSlot.getItem()) < ((PatternCatalogueItem)menu.patternSlot.getItem().getItem()).getMaxPatterns();
                icon(26, 114, ModGuiIcons.EDIT, text("menu.edit"), this::editSelected).active = selected != null;
                icon(26, 138, ModGuiIcons.DELETE, text("menu.delete"), () -> go(Page.DELETE)).active = selected != null;
                button(116, 203, 28, Component.literal("‹"), () -> switchPreview(-1));
                button(216, 203, 28, Component.literal("›"), () -> switchPreview(1));
            }
            case CREATE -> {
                int index = 0;
                for (TrafficSignShape s : TrafficSignShape.values()) {
                    int x = 26 + index % 8 * 38, y = 103 + index / 8 * 38;
                    TEButton b = addRenderableWidget(new TEButton(leftPos + x, topPos + y, 32, 32, Component.empty(), a -> startEditor(s, -1, null, text("pattern.name_unknown").getString())) {
                        @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) { super.renderWidget(g, mx, my, dt); g.blit(s.getIconResourceLocation(), getX() + 8, getY() + 8, 16, 16, 0, 0, 16, 16, 16, 16); }
                    });
                    b.setTooltip(Tooltip.create(Component.translatable(s.getTranslationKey())));
                    index++;
                }
                button(238, 202, 96, CommonComponents.GUI_CANCEL, () -> go(Page.MAIN));
            }
            case EDIT -> {
                for (int k = 0; k < 4; k++) {
                    final int t = k;
                    ModGuiIcons ico = new ModGuiIcons[]{ModGuiIcons.EDIT, ModGuiIcons.ERASE, ModGuiIcons.PICK, ModGuiIcons.FILL}[k];
                    TEButton b = icon(26, 86 + k * 22, ico, text("editor." + new String[]{"draw", "erase", "pick_color", "fill"}[k]), () -> { tool = t; buildPage(); });
                    b.selected(tool == k);
                }
                icon(26, 193, ModGuiIcons.OPEN, text("editor.load"), () -> { imported = null; importRow = 0; importError = null; go(Page.IMPORT); });
                icon(276, 193, ModGuiIcons.CHECK, text("editor.save"), this::save);
                icon(304, 193, ModGuiIcons.CANCEL, text("editor.discard"), () -> go(Page.DISCARD));
                TEButton pick = addRenderableWidget(new TEButton(leftPos + 260, topPos + 86, 60, 21, text("editor.pick_color"), b -> { if (!busy) go(Page.COLOR); }) {
                    @Override protected void renderLabel(GuiGraphics g, int foreground) {
                        g.fill(getX() + 3, getY() + 3, getX() + 21, getY() + 18, color);
                        TEPanel.outline(g, getX() + 3, getY() + 3, 18, 15, TEColors.TEXT);
                        TEWorkbenchIcons.draw(g, ModGuiIcons.PICK, getX() + 36, getY() + 3, TEColors.TEXT);
                    }
                });
                pick.setTooltip(Tooltip.create(text("editor.pick_color")));
                nameBox = addRenderableWidget(new EditBox(font, leftPos + 152, topPos + 53, 180, 18, text("editor.text")));
                nameBox.setMaxLength(20); nameBox.setTextColor(TEColors.TEXT); nameBox.setValue(name); nameBox.setResponder(value -> name = value);
            }
            case COLOR -> {
                colorPicker = addRenderableWidget(new TEColorPicker(leftPos + 26, topPos + 97, 190, 87, color, text("color.title"), value -> setEditorColor(value, false)));
                hexBox = addRenderableWidget(new EditBox(font, leftPos + 234, topPos + 153, 98, 19, Component.literal("HEX")));
                hexBox.setTextColor(TEColors.TEXT);
                hexBox.setMaxLength(6); hexBox.setFilter(v -> v.matches("[0-9a-fA-F]{0,6}"));
                hexBox.setValue(String.format("%06X", color & 0xFFFFFF));
                hexBox.setResponder(value -> { if (!updatingHex && value.length() == 6) setEditorColor(0xFF000000 | Integer.parseInt(value, 16), true); });
                for (int i = 0; i < COLOR_PRESETS.length; i++) {
                    final int preset = COLOR_PRESETS[i];
                    addRenderableWidget(new TEButton(leftPos + 26 + i * 24, topPos + 199, 21, 20, Component.literal(String.format("#%06X", preset & 0xFFFFFF)), b -> setEditorColor(preset, true)) {
                        @Override protected void renderLabel(GuiGraphics g, int foreground) { g.fill(getX() + 3, getY() + 3, getX() + 18, getY() + 17, preset); }
                        @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) {
                            super.renderWidget(g, mx, my, dt);
                            if (color == preset) TEPanel.outline(g, getX(), getY(), getWidth(), getHeight(), TEColors.SELECTION_BORDER);
                        }
                    });
                }
                button(234, 201, 98, CommonComponents.GUI_DONE, () -> go(Page.EDIT)).primary();
            }
            case IMPORT -> {
                buildImport();
                button(26, 203, 118, text("editor.import_file"), this::importFile);
                button(150, 203, 82, CommonComponents.GUI_CANCEL, () -> go(Page.EDIT));
                button(238, 203, 96, CommonComponents.GUI_DONE, () -> { if (imported != null) { System.arraycopy(imported, 0, pixels, 0, 1024); dirty = true; go(Page.EDIT); } }).primary().active = imported != null;
            }
            case DISCARD -> {
                button(84,175,110,text("editor.discard"),()->go(Page.MAIN));
                button(201,175,85,CommonComponents.GUI_CANCEL,()->go(Page.EDIT));
            }
            case DELETE -> {
                button(84, 175, 110, Component.translatable("selectWorld.deleteButton"), () -> {
                    int index = PatternCatalogueItem.getSelectedIndex(menu.patternSlot.getItem());
                    busy = true;
                    ModNetworkManager.DELETE_PATTERN_CATALOG_ENTRY.send(NetworkDirection.toServer(), new PatternCatalogueDeletePacket.Request(index), r -> { busy = false; go(Page.MAIN); }, () -> { busy = false; });
                });
                button(201, 175, 85, CommonComponents.GUI_CANCEL, () -> go(Page.MAIN));
            }
        }
    }
    private void setEditorColor(int value, boolean updatePicker) {
        color = 0xFF000000 | value;
        colorCaption = String.format("#%06X", color & 0xFFFFFF);
        if (updatePicker && colorPicker != null) colorPicker.setColor(color);
        if (hexBox != null) {
            updatingHex = true;
            hexBox.setValue(String.format("%06X", color & 0xFFFFFF));
            updatingHex = false;
        }
    }
    private void buildImport() {
        importButtons.forEach(this::removeWidget); importButtons.clear();
        var all = library.shape(shape, false);
        int cols = 8, rows = 4;
        importRow = Math.max(0, Math.min(importRow, (all.size() + cols - 1) / cols - rows));
        for (int i = importRow * cols; i < Math.min(all.size(), (importRow + rows) * cols); i++) {
            var entry = all.get(i);
            int bx = 26 + i % cols * 24, by = 93 + (i / cols - importRow) * 24;
            TEButton b = addRenderableWidget(new TEButton(leftPos + bx, topPos + by, 22, 22, Component.empty(), a -> {
                try (var in = minecraft.getResourceManager().getResource(entry.texture()).orElseThrow().open(); NativeImage image = NativeImage.read(in)) { imported = imagePixels(image); go(Page.IMPORT); }
                catch (IOException ex) { importError = ex.getMessage(); }
            }) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float dt) { super.renderWidget(g, mx, my, dt); g.blit(entry.texture(), getX() + 2, getY() + 2, 18, 18, 0, 0, entry.width(), entry.height(), entry.width(), entry.height()); }
            });
            importButtons.add(b);
        }
    }
    public void updatePreview() {
        if (minecraft.screen != this) return;
        NamedTrafficSignTextureReference ref = cataloguePresent() ? PatternCatalogueItem.getSelectedPattern(menu.patternSlot.getItem()) : null;
        if (Objects.equals(ref, selected) && (ref == null || preview != null)) return;
        if (preview != null) preview.close();
        selected = ref;
        preview = ref == null ? null : TrafficSignClientTexture.load(ref.getTextureId(), false, null);
    }
    private void switchPreview(int delta) {
        ItemStack stack = menu.patternSlot.getItem();
        int count = PatternCatalogueItem.getStoredPatternCount(stack);
        if (count == 0) return;
        int index = Math.max(0, Math.min(count - 1, PatternCatalogueItem.getSelectedIndex(stack) + delta));
        PatternCatalogueItem.setSelectedIndex(stack, index); buildPage();
        ModNetworkManager.UPDATE_PATTERN_CATALOG_INDEX_IN_GUI.send(NetworkDirection.toServer(), new PatternCatalogueIndexPacketGui.Request(index), r -> updatePreview(), () -> {});
    }
    private void startEditor(TrafficSignShape shape, int index, int[] source, String name) {
        this.shape = shape; editIndex = index; this.name = name; tool = 0;
        Arrays.fill(pixels, 0); if (source != null) System.arraycopy(source, 0, pixels, 0, 1024);
        dirty = true; go(Page.EDIT);
    }
    private void editSelected() {
        if (selected == null || preview == null || !preview.isFullyLoaded()) return;
        try {
            shape = preview.getRawData().getShape();
            int[] data;
            if (preview.isBuiltIn()) {
                try (var in = minecraft.getResourceManager().getResource(preview.getTextureLocation()).orElseThrow().open(); NativeImage image = NativeImage.read(in)) { data = imagePixels(image); }
            } else {
                try (NativeImage image = NativeImage.read(preview.getRawData().getPixelData())) { data = imagePixels(image); }
            }
            startEditor(preview.getRawData().getShape(), PatternCatalogueItem.getSelectedIndex(menu.patternSlot.getItem()), data, selected.getName());
        } catch (IOException ex) { TrafficEngine.LOGGER.warn("Cannot edit sign", ex); }
    }
    private static int swapRedBlue(int argb) { return (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16); }
    private int[] imagePixels(NativeImage image) {
        int[] result = new int[1024];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if (shape == null || shape.isPixelValid(x, y)) result[y * 32 + x] = swapRedBlue(image.getPixelRGBA(x * image.getWidth() / 32, y * image.getHeight() / 32));
        return result;
    }
    private void importFile() {
        PointerBuffer filters = MemoryUtil.memAllocPointer(5);
        String[] extensions = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
        for (String ext : extensions) filters.put(MemoryUtil.memAddress(MemoryUtil.memUTF8(ext)));
        filters.flip();
        minecraft.getSoundManager().pause();
        try {
            String path = TinyFileDialogs.tinyfd_openFileDialog(text("editor.import_file").getString(), (CharSequence)null, filters, "Image Files", false);
            if (path == null) return;
            BufferedImage image = ImageIO.read(new File(path));
            if (image == null) throw new IOException("Unsupported image");
            int[] result = new int[1024];
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if (shape.isPixelValid(x, y)) result[y * 32 + x] = image.getRGB(x * image.getWidth() / 32, y * image.getHeight() / 32);
            imported = result; importError = null; go(Page.IMPORT);
        } catch (IOException ex) { importError = text("editor.import_error").getString(); TrafficEngine.LOGGER.warn("Cannot import sign", ex); }
        finally { minecraft.getSoundManager().resume(); for (int i = 0; i < filters.limit(); i++) MemoryUtil.nmemFree(filters.get(i)); MemoryUtil.memFree(filters); }
    }
    private void save() {
        if (!cataloguePresent()) return;
        try (NativeImage image = new NativeImage(32, 32, false)) {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) image.setPixelRGBA(x, y, shape.isPixelValid(x, y) ? swapRedBlue(pixels[y * 32 + x]) : 0);
            TrafficSignTextureData data = TrafficSignClientTexture.createNew(shape, image, null);
            busy = true;
            ModNetworkManager.UPDATE_TRAFFIC_SIGN_PATTERN.send(NetworkDirection.toServer(), new TrafficSignPatternPacket.Request(NamedTrafficSignTextureReference.of(data, name), editIndex), r -> { busy = false; go(Page.MAIN); }, () -> { busy = false; });
        }
    }
    private boolean canvasContains(double x, double y) { return x >= leftPos + CANVAS_X && x < leftPos + CANVAS_X + CANVAS_SIZE && y >= topPos + CANVAS_Y && y < topPos + CANVAS_Y + CANVAS_SIZE; }
    private int pixelAt(double x, double y) { return (int)(y - topPos - CANVAS_Y) / 4 * 32 + (int)(x - leftPos - CANVAS_X) / 4; }
    private void paint(int position, int mouseButton) {
        int x = position % 32, y = position / 32;
        if (!shape.isPixelValid(x, y)) return;
        if (tool == 2) { if (pixels[position] != 0) setEditorColor(pixels[position], false); return; }
        int value = mouseButton == 1 || tool == 1 ? 0 : color;
        if (tool == 3) {
            int replace = pixels[position]; if (replace == value) return;
            int[] queue = new int[1024]; int head = 0, end = 1; queue[0] = position; pixels[position] = value;
            while (head < end) {
                int p = queue[head++];
                for (int direction = 0; direction < 4; direction++) {
                    int neighbor = p + switch(direction) {case 0 -> -1;case 1 -> 1;case 2 -> -32;default -> 32;};
                    if (neighbor < 0 || neighbor >= 1024 || Math.abs(neighbor % 32 - p % 32) + Math.abs(neighbor / 32 - p / 32) != 1 || !shape.isPixelValid(neighbor % 32, neighbor / 32) || pixels[neighbor] != replace) continue;
                    pixels[neighbor] = value; queue[end++] = neighbor;
                }
            }
        } else pixels[position] = value;
        dirty = true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) { x /= guiScale; y /= guiScale;
        if (page == Page.EDIT && canvasContains(x, y) && !busy && button <= 1) { lastPixel = pixelAt(x, y); paint(lastPixel, button); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { x /= guiScale; y /= guiScale; dx /= guiScale; dy /= guiScale;
        if (page == Page.EDIT && canvasContains(x, y) && !busy && button <= 1) {
            int next = pixelAt(x, y);
            if (tool <= 1 && lastPixel >= 0) {
                int ax = lastPixel % 32, ay = lastPixel / 32, bx = next % 32, by = next / 32;
                int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
                for (int i = 1; i <= steps; i++) paint(Math.round(ay + (by - ay) * i / (float)steps) * 32 + Math.round(ax + (bx - ax) * i / (float)steps), button);
            } else if (next != lastPixel) paint(next, button);
            lastPixel = next; return true;
        }
        lastPixel = -1; return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { x /= guiScale; y /= guiScale; lastPixel = -1; return super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double delta) { x /= guiScale; y /= guiScale;
        if (page == Page.IMPORT) { importRow += delta > 0 ? -1 : 1; buildImport(); return true; }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int code, int scan, int modifiers) {
        if (code == 256 && page != Page.MAIN && !busy) { go(page == Page.IMPORT || page == Page.COLOR || page == Page.DISCARD ? Page.EDIT : page == Page.EDIT ? Page.DISCARD : Page.MAIN); return true; }
        return super.keyPressed(code, scan, modifiers);
    }
    @Override public void containerTick() {
        super.containerTick();
        if (!ItemStack.matches(lastCatalogue, menu.patternSlot.getItem())) {
            lastCatalogue = menu.patternSlot.getItem().copy();
            if (!cataloguePresent()) { page = Page.MAIN; buildPage(); }
            else if (page == Page.MAIN) buildPage();
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,TEColors.MUTED,false);
    }
    @Override public void removed() {
        if (preview != null) { preview.close(); preview = null; }
        if (canvasLocation != null) { minecraft.getTextureManager().release(canvasLocation); canvasLocation = null; canvasTexture = null; textureSource = null; }
        super.removed();
    }
    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        TEChrome.draw(g, font, leftPos, topPos, imageWidth, imageHeight, title);
        TEPanel.draw(g, leftPos + 69, topPos + 233, 222, 58);
        for (var slot : menu.slots) { g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, TEColors.BORDER); g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, TEColors.FIELD); }
        TEPanel.draw(g, leftPos + 16, topPos + 78, 328, 146);
        g.drawString(font, text("catalogue"), leftPos + 43, topPos + 58, TEColors.MUTED, false);
        if (!cataloguePresent()) { g.drawWordWrap(font, text("menu.no_pattern"), leftPos + 92, topPos + 131, 182, TEColors.MUTED); return; }
        switch (page) {
            case MAIN -> {
                if (preview != null && preview.isFullyLoaded()) {
                    var d = preview.getRawData();
                    if (d.getWidth() > 0 && d.getHeight() > 0) g.blit(preview.getTextureLocation(), leftPos + 130, topPos + 90, 100, 100, 0, 0, d.getWidth(), d.getHeight(), d.getWidth(), d.getHeight());
                    g.drawString(font, font.plainSubstrByWidth(selected.getName(), 164), leftPos + 111, topPos + 191, TEColors.TEXT, false);
                }
                g.drawCenteredString(font, selected == null ? "0" : Integer.toString(PatternCatalogueItem.getSelectedIndex(menu.patternSlot.getItem()) + 1), leftPos + 180, topPos + 208, TEColors.MUTED);
            }
            case CREATE -> g.drawString(font, text("createpattern.instruction"), leftPos + 26, topPos + 86, TEColors.MUTED, false);
            case EDIT -> {
                renderPixelPreview(g, pixels, leftPos + CANVAS_X, topPos + CANVAS_Y, CANVAS_SIZE);
                TEPanel.outline(g, leftPos + CANVAS_X - 1, topPos + CANVAS_Y - 1, CANVAS_SIZE + 2, CANVAS_SIZE + 2, TEColors.BORDER);
                g.drawString(font, colorCaption, leftPos + 260, topPos + 113, TEColors.TEXT, false);
            }
            case COLOR -> {
                g.drawString(font, text("color.title"), leftPos + 26, topPos + 84, TEColors.TEXT, false);
                g.fill(leftPos + 234, topPos + 97, leftPos + 332, topPos + 126, color);
                TEPanel.outline(g, leftPos + 234, topPos + 97, 98, 29, TEColors.BORDER);
                g.drawString(font, "HEX", leftPos + 234, topPos + 139, TEColors.MUTED, false);
            }
            case IMPORT -> {
                TEPanel.draw(g, leftPos + 234, topPos + 93, 100, 96);
                if (imported != null) renderPixelPreview(g, imported, leftPos + 248, topPos + 105, 72);
                if (importError != null) g.drawString(font, font.plainSubstrByWidth(importError, 304), leftPos + 26, topPos + 191, 0xFFFF8888, false);
            }
            case DISCARD -> g.drawWordWrap(font,text("discard.question"),leftPos+84,topPos+107,205,TEColors.TEXT);
            case DELETE -> { g.drawWordWrap(font, text("delete.question"), leftPos + 84, topPos + 107, 205, TEColors.TEXT); if (selected != null) g.drawString(font, font.plainSubstrByWidth(selected.getName(), 205), leftPos + 84, topPos + 139, TEColors.MUTED, false); }
        }
    }
    private void renderPixelPreview(GuiGraphics g, int[] source, int x, int y, int size) {
        if (canvasTexture == null) {
            canvasTexture = new DynamicTexture(new NativeImage(32, 32, false));
            canvasLocation = minecraft.getTextureManager().register("trafficengine_editor", canvasTexture);
        }
        if (textureSource != source || dirty) {
            NativeImage image = canvasTexture.getPixels();
            for (int py = 0; py < 32; py++) for (int px = 0; px < 32; px++) {
                int pixel = source[py * 32 + px];
                int background = (px + py) % 2 == 0 ? 0xFFE9E9E9 : 0xFFD9D9D9;
                image.setPixelRGBA(px, py, shape.isPixelValid(px, py) ? swapRedBlue(pixel == 0 ? background : pixel) : 0);
            }
            canvasTexture.upload(); textureSource = source; dirty = false;
        }
        RenderSystem.enableBlend();
        g.blit(canvasLocation, x, y, size, size, 0, 0, 32, 32, 32, 32);
        RenderSystem.disableBlend();
    }
    @Override public void mouseMoved(double x, double y) {super.mouseMoved(x / guiScale, y / guiScale);}
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderBackground(g);
        g.pose().pushPose();g.pose().scale((float)guiScale,(float)guiScale,1);
        int x=(int)(mx/guiScale),y=(int)(my/guiScale);
        super.render(g,x,y,tick);renderTooltip(g,x,y);
        g.pose().popPose();
    }
}
