package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEIntegerSlider;
import com.destan.trafficengine.network.packets.cts.StreetLampConfigPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.util.ETimeFormat;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.util.GameTime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class StreetLampScheduleScreen extends TrafficEngineScreen {
    private int turnOnTime, turnOffTime;
    private ETimeFormat timeFormat;
    private TEIntegerSlider onSlider, offSlider;
    private final int day = (int)GameTime.TICKS_PER_DAY;
    private final int offset = (int)GameTime.DAYTIME_OFFSET;
    public StreetLampScheduleScreen(int on, int off, ETimeFormat format) {
        super(Component.translatable("gui.trafficengine.streetlampconfig.title"));
        turnOnTime = on; turnOffTime = off; timeFormat = format;
    }
    private Component caption(String key, int sliderTime) {
        int ticks = Math.floorMod(sliderTime - offset, day);
        String formatted = new GameTime(ticks).format(timeFormat);
        return Component.translatable("gui.trafficengine.streetlampconfig." + key).append(": ").append(formatted);
    }
    private Component formatLabel() {
        return Component.translatable("gui.trafficengine.streetlampconfig.time_format").append(": ").append(timeFormat.getValueTranslation());
    }
    @Override protected void init() {
        layoutWindow(350, 190);
        int x = left + 16, w = windowWidth - 32;
        addRenderableWidget(new TEButton(x, top + 49, w, 20, formatLabel(), b -> {
            timeFormat = ETimeFormat.values()[(timeFormat.ordinal() + 1) % ETimeFormat.values().length];
            b.setMessage(formatLabel()); onSlider.refreshCaption(); offSlider.refreshCaption();
        }));
        int step = day / 96;
        onSlider = addRenderableWidget(new TEIntegerSlider(x, top + 79, w, 0, day - step, step,
            Math.floorMod(turnOnTime + offset, day), value -> caption("turn_on_time", value), value -> turnOnTime = Math.floorMod(value - offset, day)));
        offSlider = addRenderableWidget(new TEIntegerSlider(x, top + 109, w, 0, day - step, step,
            Math.floorMod(turnOffTime + offset, day), value -> caption("turn_off_time", value), value -> turnOffTime = Math.floorMod(value - offset, day)));
        addRenderableWidget(new TEButton(left + windowWidth - 164, top + windowHeight - 30, 72, 20, CommonComponents.GUI_CANCEL, b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 86, top + windowHeight - 30, 74, 20, CommonComponents.GUI_DONE, b -> {
            ModNetworkManager.UPDATE_STREET_LAMP_CONFIG_CARD.send(NetworkDirection.toServer(), new StreetLampConfigPacket(turnOnTime, turnOffTime, timeFormat));
            onClose();
        }).primary());
        addCloseButton();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWindow(g); super.render(g, mouseX, mouseY, partialTick);
    }
}
