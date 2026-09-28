package com.destan.trafficengine.client.widgets;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
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
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class TrafficLightScheduleEntry extends DLGuiComponent {
    private enum ColorChoice {
        OFF, RED, YELLOW, GREEN, RED_YELLOW;

        static ColorChoice from(List<TrafficLightColor> colors) {
            boolean red = colors.stream().anyMatch(c -> c.isSimilar(TrafficLightColor.RED));
            boolean yellow = colors.stream().anyMatch(c -> c.isSimilar(TrafficLightColor.YELLOW));
            boolean green = colors.stream().anyMatch(c -> c.isSimilar(TrafficLightColor.GREEN));
            if (red && yellow) return RED_YELLOW;
            if (red) return RED;
            if (yellow) return YELLOW;
            if (green) return GREEN;
            return OFF;
        }

        List<TrafficLightColor> colors() {
            return switch (this) {
                case RED -> List.of(TrafficLightColor.RED);
                case YELLOW -> List.of(TrafficLightColor.YELLOW);
                case GREEN -> List.of(TrafficLightColor.GREEN);
                case RED_YELLOW -> List.of(TrafficLightColor.RED, TrafficLightColor.YELLOW);
                default -> List.of();
            };
        }

        Component label() {
            String key = "gui.trafficengine.trafficlightschedule.color.";
            return switch (this) {
                case RED -> TextUtils.translate(key + "red").withStyle(ChatFormatting.RED);
                case YELLOW -> TextUtils.translate(key + "yellow").withStyle(ChatFormatting.YELLOW);
                case GREEN -> TextUtils.translate(key + "green").withStyle(ChatFormatting.GREEN);
                case RED_YELLOW -> TextUtils.translate(key + "red").withStyle(ChatFormatting.RED)
                    .append(TextUtils.text(" + ").withStyle(ChatFormatting.WHITE))
                    .append(TextUtils.translate(key + "yellow").withStyle(ChatFormatting.YELLOW));
                case OFF -> TextUtils.translate(key + "off");
            };
        }
    }

    private final boolean hidePhaseId;
    private final int colorX;
    private final int durationX;

    public TrafficLightScheduleEntry(int x, int y, int width, TrafficLightScheduleEntryData entry, boolean hidePhaseId,
            Map<Integer, TrafficLightType> signalTypes, Consumer<TrafficLightScheduleEntryData> removeAction,
            BiConsumer<TrafficLightScheduleEntryData, Integer> reorderAction) {
        super(x, y, width, 54);
        this.hidePhaseId = hidePhaseId;
        this.colorX = 86;
        this.durationX = colorX + 125;
        inputConsumptionPolicy.set(c -> c != ConsumptionType.SCROLL);

        if (!hidePhaseId) {
            DLNumberPicker id = addComponent(new DLNumberPicker(10, 24, 64, 18));
            id.showButtons.set(false);
            id.min.set(-9999D);
            id.max.set(9999D);
            id.value.set((double)entry.getPhaseId());
            id.inputConsumptionPolicy.set(c -> c != ConsumptionType.MOUSE_MOVE);
            id.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
                entry.setPhaseId((int)e.value());
                return false;
            });
        }

        ColorChoice selected = ColorChoice.from(entry.getEnabledColors());
        entry.enableOnlyColors(selected.colors());
        DLCycleButton<ColorChoice> color = addComponent(new DLCycleButton<>(colorX, 24, 116, 18));
        color.items.addAll(ColorChoice.values());
        color.selectedItem.set(Optional.of(selected));
        color.textFormat.set(c -> c.selectedItem.get().map(ColorChoice::label).orElse(TextUtils.empty()));
        color.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            color.selectedItem.get().ifPresent(choice -> {
                entry.enableOnlyColors(choice.colors());
            });
            return false;
        });

        DLNumberPicker seconds = addComponent(new DLNumberPicker(durationX, 24, 62, 18));
        seconds.min.set(0D);
        seconds.max.set((double)TrafficLightScheduleEntryData.MAX_SECONDS);
        seconds.value.set(entry.getDurationSeconds());
        seconds.inputConsumptionPolicy.set(c -> c != ConsumptionType.MOUSE_MOVE);

        seconds.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
            entry.setDurationSeconds(Math.max(0D, e.value()));
            return false;
        });

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
        if (!hidePhaseId) label(graphics, 10, 11, "phase_id", ink);
        label(graphics, colorX, 11, "color", ink);
        label(graphics, durationX, 11, "seconds", ink);
    }

    private void label(DLGuiGraphics graphics, int x, int y, String key, DLColor color) {
        GuiUtils.drawString(graphics, graphics.defaultFont(), x, y,
            TextUtils.translate("gui.trafficengine.trafficlightschedule." + key), color, ETextAlignment.LEFT, false);
    }
}
