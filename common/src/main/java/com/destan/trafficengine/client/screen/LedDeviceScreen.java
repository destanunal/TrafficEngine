package com.destan.trafficengine.client.screen;

import java.util.List;
import net.minecraft.util.FormattedCharSequence;

import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.network.packets.cts.LedDevicePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.components.TEColorPicker;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.components.EditBox;
import com.destan.trafficengine.client.gui.components.TEEditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class LedDeviceScreen extends TrafficEngineScreen {
    private final Level level;
    private final Component colorLabel = Component.translatable("gui.trafficengine.led_device.color");
    private final Component messageLabel = Component.translatable("gui.trafficengine.led_device.message");
    private Component previewLabel;
    private final Component paintHint = Component.translatable("gui.trafficengine.led_device.paint_hint");
    private final BlockPos pos;
    private final LedDeviceBlockEntity blockEntity;
    private EditBox colorBox;
    private final EditBox[] messageBoxes = new EditBox[6];
    private boolean enabled;
    private long pixels;
    private final int[] pixelColors;
    private int paintingButton = -1;
    private int lastPaintX = -1, lastPaintY = -1;
    private int gridX, gridY, gridCell;
    private int paintHintY;
    private List<FormattedCharSequence> paintHintLines = List.of();
    private int selectedColor;
    private TEColorPicker colorPicker;
    private boolean updatingColorBox;
    private int paletteX, paletteY, paletteWidth, paletteHeight, messageLabelY;
    private int contentX, contentWidth, previewY, previewHeight;
    private static final int HUE_GAP = 6;
    private static final int HUE_WIDTH = 12;

    public LedDeviceScreen(Level level, BlockPos pos) {
        super(Component.translatable("gui.trafficengine.led_device.title"));
        this.level = level; this.pos = pos;
        this.blockEntity = (LedDeviceBlockEntity)level.getBlockEntity(pos);
        this.enabled = blockEntity.isManualEnabled(); this.pixels = blockEntity.getPixels();
        this.selectedColor = blockEntity.getLedColor() & 0xFFFFFF;
        this.pixelColors = blockEntity.getPixelColors();
    }

    private LedDeviceType type() { return ((LedDeviceBlock)blockEntity.getBlockState().getBlock()).getDeviceType(); }
    private int messageSlotCount() { return type().getMessageSlots(); }

    @Override protected void init() {
        paintingButton = -1;
        lastPaintX = lastPaintY = -1;
        String[] messageLines = blockEntity.getMessage().split("\\|", -1);
        if (messageBoxes[0] != null) {
            messageLines = new String[messageBoxes.length];
            for (int i = 0; i < messageBoxes.length; i++) messageLines[i] = messageBoxes[i].getValue();
        }
        String typedColor = colorBox == null ? String.format("#%06X", selectedColor) : colorBox.getValue();
        layoutWindow(480, 330);
        previewLabel = Component.translatable(type().isTrafficDisplay()
            ? "gui.trafficengine.led_device.preview" : "gui.trafficengine.led_device.pixels");
        int colorPanelWidth = Math.min(190, (windowWidth - 34) / 2);
        paletteX = left + 20;
        paletteY = top + 66;
        paletteWidth = colorPanelWidth - 16 - HUE_GAP - HUE_WIDTH;
        paletteHeight = Math.min(140, windowHeight - 144);
        contentX = left + colorPanelWidth + 24;
        contentWidth = windowWidth - colorPanelWidth - 36;
        previewY = top + 64;
        int messageFieldHeight = windowHeight < 250 ? 10 : 12;
        int messageRowStep = messageFieldHeight + 6;
        int rowGaps = Math.max(1, messageSlotCount() - 1);
        // Keep a visible gap even on a small GUI; reserve space above the footer.
        messageRowStep = Math.min(messageRowStep, Math.max(messageFieldHeight + 4,
            (windowHeight - 152 - messageFieldHeight) / rowGaps));
        int messageRowsHeight = Math.max(0, messageSlotCount() - 1) * messageRowStep + messageFieldHeight + 2;
        previewHeight = type().isTrafficDisplay()
            ? Math.max(20, Math.min(70, windowHeight - 128 - messageRowsHeight))
            : windowHeight - 140;
        int colorBoxY = paletteY + paletteHeight + 6;
        colorBox = new TEEditBox(font, paletteX + 27, colorBoxY, paletteWidth + HUE_GAP + HUE_WIDTH - 27, 18, Component.literal("HEX"));
        colorBox.setMaxLength(7);
        colorBox.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        colorBox.setValue(typedColor);
        colorBox.setResponder(this::applyTypedColor);
        colorBox.setTextColor(TEColors.TEXT);
        addRenderableWidget(colorBox);
        colorPicker = addRenderableWidget(new TEColorPicker(paletteX, paletteY,
            paletteWidth + HUE_GAP + HUE_WIDTH, paletteHeight, 0xFF000000 | selectedColor,
            colorLabel, this::updateSelectedColor));
        int messageStartY = previewY + previewHeight + 16;
        messageLabelY = messageStartY - 12;
        for (int i = 0; i < messageBoxes.length; i++) {
            EditBox box = new TEEditBox(font, contentX + 8, messageStartY + i * messageRowStep, contentWidth - 16, messageFieldHeight,
                Component.translatable("gui.trafficengine.led_device.line", i + 1));
            box.setMaxLength(24);
            box.setTextColor(TEColors.TEXT);
            box.setValue(i < messageLines.length ? messageLines[i] : "");
            box.setVisible(type().isTrafficDisplay() && i < messageSlotCount());
            messageBoxes[i] = box;
            addRenderableWidget(box);
        }
        gridCell = Math.min(18, Math.min((contentWidth - 20) / 8, previewHeight / 8));
        gridX = contentX + (contentWidth - gridCell * 8) / 2;
        gridY = previewY + (previewHeight - gridCell * 8) / 2;
        paintHintLines = font.split(paintHint, contentWidth - 16);
        int hintHeight = Math.max(0, paintHintLines.size() - 1) * (font.lineHeight + 2) + font.lineHeight;
        paintHintY = Math.max(gridY + gridCell * 8 + 5, top + windowHeight - 52 - hintHeight);
        int buttonY = top + windowHeight - 30;
        TEButton toggle = addRenderableWidget(new TEButton(left + 12, buttonY, 94, 20,
            Component.translatable(enabled ? "gui.trafficengine.led_device.on" : "gui.trafficengine.led_device.off"), b -> {
                enabled = !enabled;
                b.setMessage(Component.translatable(enabled ? "gui.trafficengine.led_device.on" : "gui.trafficengine.led_device.off"));
                ((TEButton)b).setSelected(enabled);
            }));
        toggle.setSelected(enabled);
        addRenderableWidget(new TEButton(left + windowWidth - 160, buttonY, 70, 20,
            Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 84, buttonY, 72, 20,
            Component.translatable("gui.done"), b -> saveAndClose()).primary());
        addCloseButton();
    }

    private void saveAndClose() {
        applyTypedColor(colorBox.getValue());
        int lastLine = messageSlotCount() - 1;
        while (lastLine >= 0 && messageBoxes[lastLine].getValue().isBlank()) lastLine--;
        StringBuilder message = new StringBuilder();
        for (int i = 0; i <= lastLine; i++) {
            if (i > 0) message.append('|');
            message.append(messageBoxes[i].getValue());
        }
        ModNetworkManager.UPDATE_LED_DEVICE.send(NetworkDirection.toServer(), new LedDevicePacket(pos, selectedColor, blockEntity.getIntervalTicks(), message.toString(), pixels, pixelColors, enabled));
        onClose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && isOnGrid(mouseX, mouseY)) {
            paintingButton = button;
            lastPaintX = lastPaintY = -1;
            paintAt(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOnGrid(double x, double y) {
        return type() == LedDeviceType.LED_LIGHT && gridCell > 0
            && x >= gridX && x < gridX + gridCell * 8 && y >= gridY && y < gridY + gridCell * 8;
    }

    private void paintAt(double mouseX, double mouseY) {
        if (!isOnGrid(mouseX, mouseY)) {
            // Re-entering the grid must not draw across cells outside the stroke.
            lastPaintX = lastPaintY = -1;
            return;
        }
        int x = (int)(mouseX - gridX) / gridCell;
        int y = (int)(mouseY - gridY) / gridCell;
        int fromX = lastPaintX < 0 ? x : lastPaintX;
        int fromY = lastPaintY < 0 ? y : lastPaintY;
        int steps = Math.max(Math.abs(x - fromX), Math.abs(y - fromY));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0 : (float)step / steps;
            int index = Math.round(fromY + (y - fromY) * progress) * 8
                + Math.round(fromX + (x - fromX) * progress);
            if (paintingButton == 1) pixels &= ~(1L << index);
            else {
                pixels |= 1L << index;
                pixelColors[index] = 0xFF000000 | selectedColor;
            }
        }
        lastPaintX = x;
        lastPaintY = y;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (paintingButton == button && paintingButton >= 0) {
            paintAt(x, y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (paintingButton == button && paintingButton >= 0) {
            paintingButton = -1;
            lastPaintX = lastPaintY = -1;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void updateSelectedColor(int argb) {
        selectedColor = argb & 0xFFFFFF;
        if (colorBox == null) return;
        String hex = String.format("#%06X", selectedColor);
        if (hex.equalsIgnoreCase(colorBox.getValue())) return;
        updatingColorBox = true;
        colorBox.setValue(hex);
        updatingColorBox = false;
    }

    private void applyTypedColor(String text) {
        if (updatingColorBox) return;
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) return;
        try {
            selectedColor = Integer.parseInt(hex, 16) & 0xFFFFFF;
            if (colorPicker != null) colorPicker.setColor(0xFF000000 | selectedColor);
        } catch (NumberFormatException ignored) {
        }
    }



    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWindow(graphics);
        int colorPanelWidth = contentX - left - 24;
        TEPanel.draw(graphics, left + 12, top + 47, colorPanelWidth, windowHeight - 89);
        TEPanel.draw(graphics, contentX, top + 47, contentWidth, windowHeight - 89);
        graphics.drawString(font, colorLabel, paletteX, top + 52, TEColors.MUTED, false);
        graphics.fill(paletteX, top + 61, paletteX + paletteWidth + HUE_GAP + HUE_WIDTH, top + 63, 0xFF000000 | selectedColor);
        graphics.drawString(font, "HEX", paletteX, paletteY + paletteHeight + 11, TEColors.MUTED, false);
        graphics.drawString(font, previewLabel, contentX + 8, top + 52, TEColors.MUTED, false);
        if (type().isTrafficDisplay()) {
            renderMessagePreview(graphics);
            graphics.drawString(font, messageLabel, contentX + 8, messageLabelY, TEColors.MUTED, false);
        } else {
            int hintY = paintHintY;
            for (FormattedCharSequence line : paintHintLines) {
                graphics.drawString(font, line, contentX + (contentWidth - font.width(line)) / 2,
                    hintY, TEColors.MUTED, false);
                hintY += font.lineHeight + 2;
            }
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                boolean on = (pixels & (1L << (y * 8 + x))) != 0;
                int x1 = gridX + x * gridCell, y1 = gridY + y * gridCell;
                graphics.fill(x1, y1, x1 + gridCell - 1, y1 + gridCell - 1, on ? pixelColors[y * 8 + x] : TEColors.FIELD);
                if (mouseX >= x1 && mouseX < x1 + gridCell && mouseY >= y1 && mouseY < y1 + gridCell)
                    TEPanel.outline(graphics, x1, y1, gridCell, gridCell, TEColors.ACCENT);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMessagePreview(GuiGraphics graphics) {
        int x = contentX + 8, w = contentWidth - 16;
        TEPanel.surface(graphics, x, previewY, w, previewHeight, TEColors.FIELD, TEColors.SUBTLE_BORDER);
        if (!enabled) return;
        int lastLine = messageSlotCount() - 1;
        while (lastLine >= 0 && messageBoxes[lastLine].getValue().isBlank()) lastLine--;
        int count = lastLine + 1;
        if (count == 0) return;
        float step = (previewHeight - 6f) / count;
        for (int i = 0; i < count; i++) {
            String text = messageBoxes[i].getValue();
            float scale = Math.min(step / (font.lineHeight + 1f), (w - 10f) / Math.max(1, font.width(text)));
            graphics.pose().pushPose();
            graphics.pose().translate(x + w / 2f, previewY + 3 + step * (i + 0.5f) - font.lineHeight * scale / 2f, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text) / 2, 0, 0xFF000000 | selectedColor, false);
            graphics.pose().popPose();
        }
    }
}
