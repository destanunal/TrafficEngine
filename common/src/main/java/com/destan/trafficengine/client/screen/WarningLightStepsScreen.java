package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.theme.TEColors;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Optional visual step editor; each step shows the lamps that will be on. */
final class WarningLightStepsScreen extends ScaledTrafficEngineScreen {
    private final WarningLightScreen parent;
    private final Consumer<String> save;
    private final int[] colors;
    private final int lamps;
    private final char[] steps = new char[32];
    private int length;
    WarningLightStepsScreen(WarningLightScreen parent, String sequence, int[] colors, int lamps, Consumer<String> save) {
        super(WarningLightScreen.text("steps_title")); this.parent = parent; this.save = save; this.colors = colors.clone(); this.lamps = lamps;
        length = sequence.length(); sequence.getChars(0, length, steps, 0);
    }
    @Override protected void initEditor() {
        layoutWindow(432, 300); addCloseButton();
        for (int i = 0; i < length; i++) {
            final int index = i; int x = left + 16 + i % 8 * 50, y = top + 75 + i / 8 * 44;
            TEButton button = addRenderableWidget(new TEButton(x, y, 46, 40, Component.translatable("gui.trafficengine.warning.step", i + 1), b -> {
                int mask = steps[index] - '0'; steps[index] = (char)('0' + (lamps == 1 ? (mask & 1) == 0 ? 1 : 0 : (mask + 1) % 4)); refreshEditor();
            }) {
                @Override protected void renderLabel(GuiGraphics g, int ink) {
                    TEText.drawCenteredString(g, font, Integer.toString(index + 1), getX() + width / 2, getY() + 3, TEColors.MUTED);
                    int mask = steps[index] - '0';
                    for (int lamp = 0; lamp < lamps; lamp++) {
                        int px = getX() + width / 2 - lamps * 7 + lamp * 14;
                        g.fill(px, getY() + 20, px + 11, getY() + 31, (mask & 1 << lamp) != 0 ? colors[lamp] : 0xFF101419);
                    }
                }
            });
            int mask = steps[i] - '0';
            button.setTooltip(Tooltip.create(Component.translatable("gui.trafficengine.warning.step", i + 1).copy().append(": ")
                .append(WarningLightScreen.text("step_state." + mask))));
        }
        TEButton add = addRenderableWidget(new TEButton(left + 16, top + 259, 84, 20, WarningLightScreen.text("add_step"), b -> { steps[length++] = '0'; refreshEditor(); })); add.active = length < 32;
        TEButton remove = addRenderableWidget(new TEButton(left + 106, top + 259, 84, 20, WarningLightScreen.text("remove_step"), b -> { length--; refreshEditor(); })); remove.active = length > 1;
        addRenderableWidget(new TEButton(left + windowWidth - 190, top + 259, 82, 20, Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 100, top + 259, 84, 20, Component.translatable("gui.done"), b -> { save.accept(new String(steps, 0, length)); onClose(); }).primary());
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        beginWindow(g); TEText.drawWordWrap(g, font, WarningLightScreen.text("steps_help"), left + 16, top + 45, windowWidth - 32, TEColors.MUTED);
        finishWindow(g, x, y, tick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
