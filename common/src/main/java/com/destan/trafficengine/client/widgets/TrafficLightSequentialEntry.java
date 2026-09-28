package com.destan.trafficengine.client.widgets;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.gui.widgets.render.FlatButtonRenderer;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.EAlign;
import de.mrjulsen.mcdragonlib.client.render.DLTextureSheet;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.DLSprite;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.ChatFormatting;

/** One controller direction with editable green, plus automatic or manual red timing. */
public class TrafficLightSequentialEntry extends DLGuiComponent {
    private final TrafficLightSchedule schedule;
    private final TrafficLightScheduleEntryData entry;

    public TrafficLightSequentialEntry(int x, int y, int width, TrafficLightSchedule schedule,
            TrafficLightScheduleEntryData entry, Consumer<TrafficLightScheduleEntryData> removeAction,
            BiConsumer<TrafficLightScheduleEntryData, Integer> reorderAction) {
        super(x, y, width, 54);
        this.schedule = schedule;
        this.entry = entry;
        inputConsumptionPolicy.set(c -> c != ConsumptionType.SCROLL);

        DLNumberPicker id = addComponent(new DLNumberPicker(10, 24, 60, 18));
        id.showButtons.set(false);
        id.min.set(-9999D);
        id.max.set(9999D);
        id.value.set((double)entry.getPhaseId());
        id.inputConsumptionPolicy.set(c -> c != ConsumptionType.MOUSE_MOVE);
        id.tooltip.set(new DLTooltip(List.of(TextUtils.translate("gui.trafficengine.trafficlightschedule.match_id")), 250));
        id.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
            entry.setPhaseId((int)e.value());
            return false;
        });

        DLNumberPicker seconds = addComponent(new DLNumberPicker(92, 24, 66, 18));
        seconds.min.set(0D);
        seconds.max.set((double)TrafficLightScheduleEntryData.MAX_SECONDS);
        seconds.value.set(entry.getDurationSeconds());
        seconds.inputConsumptionPolicy.set(c -> c != ConsumptionType.MOUSE_MOVE);
        seconds.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
            entry.setDurationSeconds(Math.max(0D, e.value()));
            return false;
        });

        if (schedule.isManualController()) {
            DLNumberPicker redSeconds = addComponent(new DLNumberPicker(180, 24, 66, 18));
            redSeconds.min.set(0D);
            redSeconds.max.set((double)TrafficLightScheduleEntryData.MAX_SECONDS);
            redSeconds.value.set(entry.getManualRedSeconds());
            redSeconds.inputConsumptionPolicy.set(c -> c != ConsumptionType.MOUSE_MOVE);
            redSeconds.tooltip.set(new DLTooltip(List.of(
                TextUtils.translate("gui.trafficengine.trafficlightschedule.manual_red_help")), 250));
            redSeconds.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
                entry.setManualRedSeconds(Math.max(0D, e.value()));
                return false;
            });
        }

        button(2, ModGuiIcons.MOVE_UP.getAsSprite(16, 16), "move_up", () -> reorderAction.accept(entry, -1));
        button(19, ModGuiIcons.MOVE_DOWN.getAsSprite(16, 16), "move_down", () -> reorderAction.accept(entry, 1));
        button(36, ModGuiIcons.DELETE_WHITE.getAsSprite(16, 16), "delete", () -> removeAction.accept(entry));
    }

    private void button(int y, DLSprite icon, String key, Runnable action) {
        DLButton button = addComponent(new DLButton(width() - 21, y, 16, 16));
        button.anchor.set2(EAlign.TOP, EAlign.RIGHT);
        button.componentRenderer.set(FlatButtonRenderer.INSTANCE);
        button.text.set(TextUtils.empty());
        button.icon.set(icon);
        button.inputConsumptionPolicy.set(p -> p != ConsumptionType.MOUSE_MOVE);
        button.tooltip.set(new DLTooltip(List.of(TextUtils.translate("gui.trafficengine.trafficlightschedule." + key)), 200));
        button.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            action.run();
            return false;
        });
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        DLTextureSheet.DRAGONLIB_UI.getSprite("button_gray_normal").render(graphics, 2, 1, width() - 4, height() - 2);
        DLColor ink = DLColor.fromInt(0xFF303030);
        int cycleTicks = schedule.getSequentialCycleTicks();
        int redTicks = entry.getDurationTicks() <= 0 ? 0
            : Math.max(0, cycleTicks - entry.getDurationTicks() - schedule.getRedYellowTicks() * 2);
        label(graphics, 10, 11, "phase_id", ink);
        label(graphics, 92, 11, "green_seconds", ink);
        label(graphics, 180, 11, "red_seconds", ink);
        if (!schedule.isManualController()) {
            GuiUtils.drawString(graphics, graphics.defaultFont(), 180, 29,
                TextUtils.text(Integer.toString(redTicks / 20)).withStyle(ChatFormatting.BOLD),
                DLColor.fromInt(0xFFAA3030), ETextAlignment.LEFT, false);
        }
    }

    private void label(DLGuiGraphics graphics, int x, int y, String key, DLColor color) {
        GuiUtils.drawString(graphics, graphics.defaultFont(), x, y,
            TextUtils.translate("gui.trafficengine.trafficlightschedule." + key), color, ETextAlignment.LEFT, false);
    }
}
