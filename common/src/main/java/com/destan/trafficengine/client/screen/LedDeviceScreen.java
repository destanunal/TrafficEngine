package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.network.packets.cts.LedDevicePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public class LedDeviceScreen extends Screen {
    private final Level level;
    private final BlockPos pos;
    private final LedDeviceBlockEntity blockEntity;
    private EditBox colorBox;
    private final EditBox[] messageBoxes = new EditBox[6];
    private boolean enabled;
    private long pixels;
    private int gridX, gridY, gridCell;
    private int selectedColor;
    private float hue, saturation, value;
    private int paletteX, paletteY, paletteWidth, paletteHeight, messageLabelY;
    private static final int HUE_GAP = 6;
    private static final int HUE_WIDTH = 12;

    public LedDeviceScreen(Level level, BlockPos pos) {
        super(Component.translatable("gui.trafficengine.led_device.title"));
        this.level = level; this.pos = pos;
        this.blockEntity = (LedDeviceBlockEntity)level.getBlockEntity(pos);
        this.enabled = blockEntity.isManualEnabled(); this.pixels = blockEntity.getPixels();
        this.selectedColor = blockEntity.getLedColor() & 0xFFFFFF;
        setHsvFromRgb(selectedColor);
    }

    private LedDeviceType type() { return ((LedDeviceBlock)blockEntity.getBlockState().getBlock()).getDeviceType(); }

    @Override protected void init() {
        int x = width / 2 - 100;
        boolean compact = height < 330;
        paletteWidth = compact ? Math.min(140, width - 80) : 160;
        paletteHeight = compact ? 54 : 100;
        paletteX = width / 2 - (paletteWidth + HUE_GAP + HUE_WIDTH) / 2;
        paletteY = compact ? 42 : 66;
        int colorBoxY = paletteY + paletteHeight + 4;
        int colorBoxHeight = compact ? 16 : 20;
        colorBox = new EditBox(font, width / 2 - 45, colorBoxY, 90, colorBoxHeight, Component.literal("HEX"));
        colorBox.setMaxLength(7);
        colorBox.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        colorBox.setValue(String.format("#%06X", selectedColor));
        colorBox.setResponder(this::applyTypedColor);
        addRenderableWidget(colorBox);
        String[] messageLines = blockEntity.getMessage().split("\\|", -1);
        int messageStartY = colorBoxY + colorBoxHeight + (compact ? 14 : 18);
        messageLabelY = messageStartY - 11;
        int boxHeight = compact ? 14 : 18;
        int boxStep = boxHeight + 2;
        for (int i = 0; i < messageBoxes.length; i++) {
            EditBox box = new EditBox(font, x, messageStartY + i * boxStep, 200, boxHeight, Component.literal("Line " + (i + 1)));
            box.setMaxLength(24);
            box.setValue(i < messageLines.length ? messageLines[i] : "");
            box.setVisible(type() == LedDeviceType.TRAFFIC_DISPLAY);
            messageBoxes[i] = box;
            addRenderableWidget(box);
        }
        gridCell = compact ? 8 : 12;
        gridX = width / 2 - gridCell * 4;
        gridY = colorBoxY + colorBoxHeight + 10;
        int contentBottom = type() == LedDeviceType.TRAFFIC_DISPLAY
            ? messageStartY + messageBoxes.length * boxStep
            : gridY + gridCell * 8;
        int buttonY = Math.min(height - 24, contentBottom + 6);
        addRenderableWidget(Button.builder(Component.translatable(enabled ? "gui.trafficengine.led_device.on" : "gui.trafficengine.led_device.off"), b -> {
            enabled = !enabled; b.setMessage(Component.translatable(enabled ? "gui.trafficengine.led_device.on" : "gui.trafficengine.led_device.off"));
        }).bounds(x, buttonY, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> saveAndClose()).bounds(x + 102, buttonY, 98, 20).build());
    }

    private void saveAndClose() {
        applyTypedColor(colorBox.getValue());
        int lastLine = messageBoxes.length - 1;
        while (lastLine >= 0 && messageBoxes[lastLine].getValue().isBlank()) lastLine--;
        StringBuilder message = new StringBuilder();
        for (int i = 0; i <= lastLine; i++) {
            if (i > 0) message.append('|');
            message.append(messageBoxes[i].getValue());
        }
        ModNetworkManager.UPDATE_LED_DEVICE.send(NetworkDirection.toServer(), new LedDevicePacket(pos, selectedColor, blockEntity.getIntervalTicks(), message.toString(), pixels, enabled));
        onClose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && updateColorFromMouse(mouseX, mouseY)) return true;
        int gridSize = gridCell * 8;
        if (type() == LedDeviceType.LED_LIGHT && mouseX >= gridX && mouseX < gridX + gridSize && mouseY >= gridY && mouseY < gridY + gridSize) {
            int px = (int)(mouseX - gridX) / gridCell, py = (int)(mouseY - gridY) / gridCell;
            pixels ^= 1L << (py * 8 + px); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && updateColorFromMouse(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean updateColorFromMouse(double mouseX, double mouseY) {
        if (mouseX >= paletteX && mouseX < paletteX + paletteWidth
            && mouseY >= paletteY && mouseY < paletteY + paletteHeight) {
            saturation = Mth.clamp((float)(mouseX - paletteX) / (paletteWidth - 1), 0.0F, 1.0F);
            value = 1.0F - Mth.clamp((float)(mouseY - paletteY) / (paletteHeight - 1), 0.0F, 1.0F);
            updateSelectedColor();
            return true;
        }
        int hueX = paletteX + paletteWidth + HUE_GAP;
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH
            && mouseY >= paletteY && mouseY < paletteY + paletteHeight) {
            hue = Mth.clamp((float)(mouseY - paletteY) / (paletteHeight - 1), 0.0F, 1.0F);
            updateSelectedColor();
            return true;
        }
        return false;
    }

    private void updateSelectedColor() {
        selectedColor = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        if (colorBox != null) {
            String value = String.format("#%06X", selectedColor);
            if (!value.equalsIgnoreCase(colorBox.getValue())) colorBox.setValue(value);
        }
    }

    private void applyTypedColor(String text) {
        String value = text.startsWith("#") ? text.substring(1) : text;
        if (value.length() != 6) return;
        try {
            selectedColor = Integer.parseInt(value, 16) & 0xFFFFFF;
            setHsvFromRgb(selectedColor);
        } catch (NumberFormatException ignored) {
        }
    }

    private void setHsvFromRgb(int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta == 0.0F) hue = 0.0F;
        else if (max == red) hue = ((green - blue) / delta) / 6.0F;
        else if (max == green) hue = (2.0F + (blue - red) / delta) / 6.0F;
        else hue = (4.0F + (red - green) / delta) / 6.0F;
        if (hue < 0.0F) hue += 1.0F;
    }

    private void renderPalette(GuiGraphics graphics) {
        for (int y = 0; y < paletteHeight; y += 2) {
            float brightness = 1.0F - (float)y / (paletteHeight - 1);
            for (int x = 0; x < paletteWidth; x += 2) {
                float sat = (float)x / (paletteWidth - 1);
                int color = 0xFF000000 | Mth.hsvToRgb(hue, sat, brightness);
                graphics.fill(paletteX + x, paletteY + y, paletteX + Math.min(x + 2, paletteWidth), paletteY + Math.min(y + 2, paletteHeight), color);
            }
        }
        int hueX = paletteX + paletteWidth + HUE_GAP;
        for (int y = 0; y < paletteHeight; y += 2) {
            int color = 0xFF000000 | Mth.hsvToRgb((float)y / (paletteHeight - 1), 1.0F, 1.0F);
            graphics.fill(hueX, paletteY + y, hueX + HUE_WIDTH, paletteY + Math.min(y + 2, paletteHeight), color);
        }
        int cursorX = paletteX + Math.round(saturation * (paletteWidth - 1));
        int cursorY = paletteY + Math.round((1.0F - value) * (paletteHeight - 1));
        outline(graphics, cursorX - 2, cursorY - 2, 5, 5, 0xFFFFFFFF);
        int hueY = paletteY + Math.round(hue * (paletteHeight - 1));
        outline(graphics, hueX - 1, hueY - 1, HUE_WIDTH + 2, 3, 0xFFFFFFFF);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.trafficengine.led_device.color"), paletteX, paletteY - 20, 0xA0A0A0);
        graphics.fill(paletteX, paletteY - 10, paletteX + paletteWidth + HUE_GAP + HUE_WIDTH, paletteY - 3, 0xFF000000 | selectedColor);
        renderPalette(graphics);
        graphics.drawString(font, "HEX", width / 2 - 69, paletteY + paletteHeight + 8, 0xA0A0A0);
        if (type() == LedDeviceType.TRAFFIC_DISPLAY) graphics.drawString(font, Component.translatable("gui.trafficengine.led_device.message"), width / 2 - 100, messageLabelY, 0xA0A0A0);
        if (type() == LedDeviceType.LED_LIGHT) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                boolean on = (pixels & (1L << (y * 8 + x))) != 0;
                int x1 = gridX + x * gridCell, y1 = gridY + y * gridCell;
                graphics.fill(x1, y1, x1 + gridCell - 2, y1 + gridCell - 2, on ? 0xFF000000 | selectedColor : 0xFF202020);
            }
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
