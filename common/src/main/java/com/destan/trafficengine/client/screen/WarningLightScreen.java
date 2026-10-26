package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.WarningLightBlock;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.*;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.packets.cts.WarningLightPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Colours, named patterns and a live diagram; no numeric sequence codes are needed. */
public final class WarningLightScreen extends ScaledTrafficEngineScreen {
    private static final PaintColor[] PALETTE = {PaintColor.RED, PaintColor.ORANGE, PaintColor.YELLOW, PaintColor.LIME,
        PaintColor.GREEN, PaintColor.CYAN, PaintColor.LIGHT_BLUE, PaintColor.BLUE, PaintColor.PURPLE, PaintColor.MAGENTA,
        PaintColor.PINK, PaintColor.BROWN, PaintColor.WHITE, PaintColor.LIGHT_GRAY, PaintColor.GRAY, PaintColor.BLACK};
    private final BlockPos pos;
    private final int[] colors = new int[2];
    private final TEButton[] colorButtons = new TEButton[2], countButtons = new TEButton[2], layoutButtons = new TEButton[2], modeButtons = new TEButton[2];
    private int lamps, selectedColor, stepTicks;
    private boolean horizontal, redstone, updatingHex;
    private String steps;
    private TEColorPicker picker;
    private TEEditBox hex;
    private TEButton patternButton;
    private int rightX, rightWidth;
    private FlashSequence previewSequence;
    private Component previewLabel;
    private final Component colorLabel = text("color"), countLabel = text("count"), layoutLabel = text("layout"), patternLabel = text("pattern"), speedLabel = text("speed");

    public WarningLightScreen(Level level, BlockPos pos) {
        super(text("title")); this.pos = pos;
        // TrafficLightBlockEntity is also a ColoredBlockEntity. Read warning data directly.
        var state = level.getBlockState(pos);
        WarningLightSettings settings = level.getBlockEntity(pos) instanceof TrafficLightBlockEntity lamp
            ? lamp.getWarningSettings() : WarningLightSettings.read(new net.minecraft.nbt.CompoundTag());
        colors[0] = settings.primary(); colors[1] = settings.secondary(); redstone = settings.redstone();
        steps = settings.sequence().steps(); stepTicks = settings.sequence().stepTicks();
        lamps = state.getValue(TrafficLightBlock.MODEL).getLightsCount() == 1 ? 1 : 2;
        horizontal = state.getValue(WarningLightBlock.HORIZONTAL);
        updateSequence();
    }
    static Component text(String key) { return Component.translatable("gui.trafficengine.warning." + key); }
    private void updateSequence() { previewSequence = new FlashSequence(steps, stepTicks); }
    @Override protected void initEditor() {
        layoutWindow(488, 350); addCloseButton();
        int colorX = left + 16, colorWidth = 204;
        rightX = left + 236; rightWidth = windowWidth - 252;
        previewLabel = text("preview");
        for (int slot = 0; slot < 2; slot++) {
            final int index = slot;
            colorButtons[slot] = addRenderableWidget(new TEButton(colorX + slot * 104, top + 52, 100, 22, text("lamp." + (slot + 1)), b -> selectColor(index)) {
                @Override protected void renderLabel(GuiGraphics g, int ink) {
                    g.fill(getX() + 7, getY() + 7, getX() + 15, getY() + 15, colors[index]);
                    TEText.drawCenteredString(g, font, getMessage(), getX() + width / 2 + 5, getY() + 7, ink);
                }
            }.selected(selectedColor == slot));
        }
        picker = addRenderableWidget(new TEColorPicker(colorX, top + 86, colorWidth, 124, colors[selectedColor], text("color"), color -> changeColor(color, false)));
        for (int i = 0; i < PALETTE.length; i++) {
            PaintColor paint = PALETTE[i]; int color = paint.getTextureColor().getAsARGB();
            TEButton swatch = addRenderableWidget(new TEButton(colorX + i % 8 * 26, top + 220 + i / 8 * 24, 22, 20, paint.getValueTranslation(), b -> changeColor(color, true)) {
                @Override protected void renderLabel(GuiGraphics g, int ink) { g.fill(getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 4, color); }
            });
            swatch.setTooltip(Tooltip.create(paint.getValueTranslation()));
        }
        hex = addRenderableWidget(new TEEditBox(font, colorX + 36, top + 274, colorWidth - 36, 20, Component.literal("HEX")));
        hex.setMaxLength(7); hex.setValue(String.format("#%06X", colors[selectedColor] & 0xFFFFFF));
        hex.setFilter(WarningLightScreen::validHexInput);
        hex.setResponder(value -> {
            String digits = value.startsWith("#") ? value.substring(1) : value;
            if (!updatingHex && digits.length() == 6) changeColor(Integer.parseInt(digits, 16), true);
        });
        int half = (rightWidth - 6) / 2;
        for (int i = 0; i < 2; i++) {
            final int choice = i;
            countButtons[i] = addRenderableWidget(new TEButton(rightX + i * (half + 6), top + 146, half, 20, text("count." + (i + 1)), b -> changeCount(choice + 1)).selected(lamps == i + 1));
            layoutButtons[i] = addRenderableWidget(new TEButton(rightX + i * (half + 6), top + 184, half, 20, text(i == 0 ? "horizontal" : "vertical"), b -> {
                horizontal = choice == 0; updateChoices();
            }).selected(horizontal == (i == 0)));
            modeButtons[i] = addRenderableWidget(new TEButton(rightX + i * (half + 6), top + 286, half, 20, text(i == 0 ? "always" : "redstone"), b -> {
                redstone = choice == 1; updateChoices();
            }).selected(redstone == (i == 1)));
        }
        patternButton = addRenderableWidget(new TEButton(rightX, top + 222, rightWidth - 76, 20, patternTitle(), b -> nextPattern()));
        addRenderableWidget(new TEButton(rightX + rightWidth - 70, top + 222, 70, 20, text("edit_steps"), b -> minecraft.setScreen(new WarningLightStepsScreen(this, steps, colors, lamps, value -> {
            steps = value; updateSequence();
        }))));
        addRenderableWidget(new DurationSlider(rightX, top + 260, rightWidth));
        addRenderableWidget(new TEButton(left + 16, top + windowHeight - 32, 94, 20, Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 110, top + windowHeight - 32, 94, 20, Component.translatable("gui.done"), b -> {
            ModNetworkManager.UPDATE_WARNING_LIGHT.send(NetworkDirection.C2S,
                new WarningLightPacket(pos, new WarningLightSettings(colors[0], colors[1], new FlashSequence(steps, stepTicks), redstone), lamps, horizontal));
            onClose();
        }).primary());
        updateChoices();
    }
    private static boolean validHexInput(String value) {
        int start = value.startsWith("#") ? 1 : 0;
        if (value.length() - start > 6) return false;
        for (int i = start; i < value.length(); i++) if (Character.digit(value.charAt(i), 16) < 0) return false;
        return true;
    }
    private void selectColor(int index) {
        selectedColor = index; picker.setColor(colors[index]); updateHex();
        for (int i = 0; i < 2; i++) colorButtons[i].setSelected(selectedColor == i);
    }
    private void changeColor(int color, boolean movePicker) {
        colors[selectedColor] = 0xFF000000 | color;
        if (movePicker && picker != null) picker.setColor(color);
        updateHex();
    }
    private void updateHex() {
        if (hex == null) return;
        updatingHex = true; hex.setValue(String.format("#%06X", colors[selectedColor] & 0xFFFFFF)); updatingHex = false;
    }
    private void changeCount(int count) {
        WarningLightPattern preset = WarningLightPattern.find(steps, lamps); lamps = count;
        if (preset != null) steps = preset.steps(lamps);
        if (lamps == 1) selectColor(0);
        updateSequence(); updateChoices();
    }
    private void updateChoices() {
        for (int i = 0; i < 2; i++) {
            countButtons[i].setSelected(lamps == i + 1); layoutButtons[i].setSelected(horizontal == (i == 0)); modeButtons[i].setSelected(redstone == (i == 1));
        }
        colorButtons[1].visible = colorButtons[1].active = lamps == 2;
        colorButtons[0].setWidth(lamps == 1 ? 204 : 100);
        if (lamps == 1 && getFocused() == colorButtons[1]) setFocused(colorButtons[0]);
        patternButton.setMessage(patternTitle());
    }
    private Component patternTitle() {
        WarningLightPattern pattern = WarningLightPattern.find(steps, lamps);
        return pattern == null ? text("pattern.custom") : Component.translatable(pattern.key());
    }
    private void nextPattern() {
        WarningLightPattern current = WarningLightPattern.find(steps, lamps);
        WarningLightPattern next = current == null ? WarningLightPattern.BLINK : current == WarningLightPattern.BLINK
            ? lamps == 1 ? WarningLightPattern.DOUBLE : WarningLightPattern.ALTERNATE
            : current == WarningLightPattern.ALTERNATE ? WarningLightPattern.DOUBLE : WarningLightPattern.BLINK;
        steps = next.steps(lamps); updateSequence(); patternButton.setMessage(patternTitle());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        beginWindow(g);
        TEText.drawString(g, font, colorLabel, left + 16, top + 40, TEColors.MUTED, false);
        TEText.drawString(g, font, "HEX", left + 16, top + 280, TEColors.MUTED, false);
        TEText.drawString(g, font, countLabel, rightX, top + 134, TEColors.MUTED, false);
        TEText.drawString(g, font, layoutLabel, rightX, top + 172, TEColors.MUTED, false);
        TEText.drawString(g, font, patternLabel, rightX, top + 210, TEColors.MUTED, false);
        TEText.drawString(g, font, speedLabel, rightX, top + 248, TEColors.MUTED, false);
        TEPanel.draw(g, rightX, top + 48, rightWidth, 76);
        TEText.drawString(g, font, previewLabel, rightX + 8, top + 53, TEColors.ACCENT, false);
        int mask = previewSequence.mask(minecraft.level.getGameTime());
        drawLamps(g, rightX + rightWidth / 2, top + 94, lamps, horizontal, mask, colors);
        finishWindow(g, mx, my, tick);
    }
    static void drawLamps(GuiGraphics g, int centerX, int centerY, int count, boolean horizontal, int mask, int[] colors) {
        int size = count == 2 && !horizontal ? 18 : 24, step = size + 6;
        int x = centerX - size / 2 - (count == 2 && horizontal ? step / 2 : 0);
        int y = centerY - size / 2 - (count == 2 && !horizontal ? step / 2 : 0);
        int w = horizontal ? size + (count - 1) * step : size, h = horizontal ? size : size + (count - 1) * step;
        TEPanel.surface(g, x - 5, y - 4, w + 10, h + 8, 0xFF242C31, 0xFF505C63);
        for (int lamp = 0; lamp < count; lamp++) {
            int px = x + (horizontal ? lamp * step : 0), py = y + (horizontal ? 0 : lamp * step);
            TrafficLightTextureManager.renderWarningPreview(g, px, py, size, (mask & 1 << lamp) != 0, colors[lamp]);
        }
    }
    private final class DurationSlider extends AbstractSliderButton {
        DurationSlider(int x, int y, int width) { super(x, y, width, 20, Component.empty(), (stepTicks - 2) / 198D); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("gui.trafficengine.warning.duration", stepTicks * 50)); }
        @Override protected void applyValue() { stepTicks = 2 + (int)Math.round(value * 198); updateSequence(); }
        @Override public void renderWidget(GuiGraphics g, int x, int y, float tick) {
            TEPanel.surface(g, getX(), getY(), width, height, TEColors.CONTROL, isHoveredOrFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
            int knob = getX() + 3 + (int)Math.round(value * (width - 10));
            g.fill(getX() + 3, getY() + height - 4, knob + 4, getY() + height - 2, TEColors.ACCENT);
            TEText.drawCenteredString(g, font, getMessage(), getX() + width / 2, TEText.centeredY(g,font,getMessage(),getY(),height), TEColors.TEXT);
        }
    }
}
