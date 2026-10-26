package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPaintToolButton;
import com.destan.trafficengine.data.PixelPattern;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Shared stencil editor with explicit pencil and eraser tools; transparent/dark PNG pixels stay blank. */
public final class PixelPatternScreen extends Screen {
    private final Screen parent;
    private final Consumer<PixelPattern> save;
    private long[] bits;
    private int left, top, cell, frameX, frameY, frameWidth, frameHeight;
    private int lastX = -1, lastY = -1;
    private boolean erasing, painting;
    private TEPaintToolButton pencilButton, eraserButton;
    private final com.destan.trafficengine.data.PixelBrush brush = new com.destan.trafficengine.data.PixelBrush(8);
    private com.destan.trafficengine.client.gui.components.TEIntegerSlider sizeSlider;
    private String error;
    public PixelPatternScreen(Screen parent, PixelPattern pattern, Consumer<PixelPattern> save) {
        super(text("title")); this.parent = parent; this.bits = pattern.bits(); this.save = save;
    }
    private static Component text(String key) { return Component.translatable("gui.trafficengine.stencil." + key); }
    @Override protected void init() {
        cell = Math.max(3, Math.min(10, (height - 144) / 16));
        endStroke();
        frameWidth = Math.min(340, width - 12); frameHeight = cell * 16 + 132;
        frameX = (width - frameWidth) / 2; frameY = (height - frameHeight) / 2;
        left = width / 2 - cell * 8; top = frameY + 76;
        pencilButton = addRenderableWidget(new TEPaintToolButton(left - 32, top, 22, false, b -> selectTool(false)));
        eraserButton = addRenderableWidget(new TEPaintToolButton(left - 32, top + 28, 22, true, b -> selectTool(true)));
        sizeSlider = addRenderableWidget(new com.destan.trafficengine.client.gui.components.TEIntegerSlider(
            width / 2 - 60, frameY + 42, 120, 1, 8, 1, brush.size(),
            n -> Component.translatable("gui.trafficengine.drawing.size", n), brush::setSize));
        selectTool(erasing);
        int y = top + cell * 16 + 12, buttonWidth = (frameWidth - 42) / 4, x = frameX + 12;
        addRenderableWidget(new TEButton(x, y, buttonWidth, 20, text("import"), b -> importPng()));
        addRenderableWidget(new TEButton(x + buttonWidth + 6, y, buttonWidth, 20, text("clear"), b -> { bits = new long[4]; endStroke(); }));
        addRenderableWidget(new TEButton(x + (buttonWidth + 6) * 2, y, buttonWidth, 20, Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new TEButton(x + (buttonWidth + 6) * 3, y, buttonWidth, 20, Component.translatable("gui.done"), b -> {
            save.accept(PixelPattern.of(bits)); minecraft.setScreen(parent);
        }).primary());
    }
    private void importPng() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String path = TinyFileDialogs.tinyfd_openFileDialog(text("import").getString(), (CharSequence)null,
                stack.pointers(stack.UTF8("*.png")), "PNG", false);
            if (path == null) return;
            File file = new File(path);
            if (file.length() > 4 * 1024 * 1024) throw new IOException("Image too large");
            // Inspect dimensions before decoding to avoid allocating a huge imported image.
            try (var input = ImageIO.createImageInputStream(file)) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Unsupported image");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw new IOException("Image too large");
                    BufferedImage image = reader.read(0);
                    long[] imported = new long[4];
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                        int rgb = image.getRGB(x * image.getWidth() / 16, y * image.getHeight() / 16);
                        int luminance = ((rgb >>> 16 & 255) * 299 + (rgb >>> 8 & 255) * 587 + (rgb & 255) * 114) / 1000;
                        if ((rgb >>> 24) >= 128 && luminance >= 128) {
                            int index = y * 16 + x; imported[index >>> 6] |= 1L << (index & 63);
                        }
                    }
                    bits = imported; error = null;
                } finally { reader.dispose(); }
            }
        } catch (IOException | RuntimeException ex) { error = text("import_error").getString(); }
    }
    private boolean paint(double x, double y, int button) {
        if (button != 0) return false;
        if (x < left || y < top || x >= left + cell * 16 || y >= top + cell * 16) {
            lastX = lastY = -1;
            return false;
        }
        int nextX = (int)(x - left) / cell, nextY = (int)(y - top) / cell;
        int px = lastX < 0 ? nextX : lastX, py = lastY < 0 ? nextY : lastY;
        brush.stroke(px, py, nextX, nextY, 16, index -> {
            if (erasing) bits[index >>> 6] &= ~(1L << (index & 63));
            else bits[index >>> 6] |= 1L << (index & 63);
        });
        lastX = nextX; lastY = nextY; return true;
    }
    private void endStroke() { painting = false; lastX = lastY = -1; }
    private void selectTool(boolean erase) {
        erasing = erase; endStroke();
        pencilButton.setSelected(!erase); eraserButton.setSelected(erase);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (x >= left - 34 && x < left + cell * 16 && y >= frameY + 40 && y < top + cell * 16) {
            sizeSlider.setIntValue(brush.size() + (int)Math.signum(delta));
            endStroke(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        endStroke();
        if (button == 0 && paint(x, y, button)) { painting = true; return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (painting && button == 0) { paint(x, y, button); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean wasPainting = painting && button == 0;
        if (button == 0) endStroke();
        return super.mouseReleased(x, y, button) || wasPainting;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The canvas owns its background; NeoForge must not blur the already drawn grid.
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        g.flush();
        g.fill(0, 0, width, height, 0xD0181B22);
        com.destan.trafficengine.client.gui.TEChrome.draw(g, font, frameX, frameY, frameWidth, frameHeight, title);
        g.flush();
        // Opaque gaps keep the sky/world from washing out parts of the checkerboard.
        // Explicit layers also keep queued background/text batches behind the canvas.
        g.pose().pushPose(); g.pose().translate(0, 0, 10);
        g.fill(left - 2, top - 2, left + cell * 16 + 1, top + cell * 16 + 1, 0xFF10151B);
        g.flush(); g.pose().translate(0, 0, 1);
        for (int py = 0; py < 16; py++) for (int px = 0; px < 16; px++) {
            int index = py * 16 + px;
            int color = (bits[index >>> 6] & 1L << (index & 63)) != 0 ? 0xFFF5D771 : ((px + py) & 1) == 0 ? 0xFF333943 : 0xFF242A33;
            g.fill(left + px * cell, top + py * cell, left + (px + 1) * cell - 1, top + (py + 1) * cell - 1, color);
        }
        if (x >= left && y >= top && x < left + cell * 16 && y < top + cell * 16) {
            int bx = brush.start((x - left) / cell), by = brush.start((y - top) / cell);
            int x0 = Math.max(0, bx), y0 = Math.max(0, by);
            com.destan.trafficengine.client.gui.components.TEPanel.outline(g, left + x0 * cell, top + y0 * cell,
                (Math.min(16, bx + brush.size()) - x0) * cell,
                (Math.min(16, by + brush.size()) - y0) * cell, 0xFFF2C45C);
        }
        g.flush(); g.pose().popPose();
        if (error != null) TEText.drawCenteredString(g, font, error, width / 2, top + cell * 16 + 35, 0xFFFF7777);
        super.render(g, x, y, tick);
    }
}
