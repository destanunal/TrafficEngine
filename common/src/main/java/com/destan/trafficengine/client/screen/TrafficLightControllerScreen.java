package com.destan.trafficengine.client.screen;

import java.util.List;
import com.destan.trafficengine.Constants;
import com.destan.trafficengine.block.entity.TrafficLightControllerBlockEntity;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.network.packets.cts.TrafficLightControllerPacket;
import com.destan.trafficengine.network.packets.cts.TrafficLightSchedulePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.util.Clipboard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class TrafficLightControllerScreen extends TrafficEngineScreen {
    private final BlockPos blockPos;
    private final Level level;
    private boolean status;
    private TEButton pasteButton;
    private final Component statusLabel = Component.translatable("gui.trafficengine.trafficlightcontroller.status");

    public TrafficLightControllerScreen(BlockPos pos, Level level) {
        super(Component.translatable("gui.trafficengine.trafficlightcontroller.title"));
        this.level = level;
        this.blockPos = pos;
        TrafficLightControllerBlockEntity blockEntity = getBlockEntity();
        status = blockEntity != null && blockEntity.isRunning();
    }
    public TrafficLightControllerBlockEntity getBlockEntity() {
        return level.getBlockEntity(blockPos) instanceof TrafficLightControllerBlockEntity entity ? entity : null;
    }
    private Component statusText() {
        return statusLabel.copy().append(": ").append(status ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
    @Override protected void init() {
        layoutWindow(390, 204);
        int x = left + 20, w = windowWidth - 40;
        TEButton toggle = addRenderableWidget(new TEButton(x, top + 54, w, 22, statusText(), b -> {
            status = !status;
            b.setMessage(statusText());
            ((TEButton)b).setSelected(status);
        }));
        toggle.setSelected(status);
        addRenderableWidget(new TEButton(x, top + 84, w, 22,
            Component.translatable("gui.trafficengine.trafficlightcontroller.edit_schedule"), b ->
                minecraft.setScreen(new TrafficLightScheduleEditor(this, level, blockPos))));
        int half = (w - 6) / 2;
        addRenderableWidget(new TEButton(x, top + 114, half, 22, Constants.textCopy, b -> {
            TrafficLightControllerBlockEntity entity = getBlockEntity();
            if (entity != null) Clipboard.put(TrafficLightSchedule.class, entity.getScheduleForEditing());
        }));
        pasteButton = addRenderableWidget(new TEButton(x + half + 6, top + 114, half, 22, Constants.textPaste, b ->
            Clipboard.get(TrafficLightSchedule.class).ifPresent(schedule ->
                ModNetworkManager.UPDATE_TRAFFIC_LIGHT_SCHEDULE.send(NetworkDirection.toServer(),
                    new TrafficLightSchedulePacket(blockPos, List.of(schedule))))));
        pasteButton.active = Clipboard.contains(TrafficLightSchedule.class);
        addRenderableWidget(new TEButton(left + windowWidth - 164, top + windowHeight - 30, 72, 20,
            CommonComponents.GUI_CANCEL, b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 86, top + windowHeight - 30, 74, 20,
            CommonComponents.GUI_DONE, b -> onDone()).primary());
        addCloseButton();
    }
    protected void onDone() {
        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_CONTROLLER.send(NetworkDirection.toServer(), new TrafficLightControllerPacket(blockPos, status));
        onClose();
    }
    @Override public void tick() { pasteButton.active = Clipboard.contains(TrafficLightSchedule.class); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWindow(g);
        TEPanel.draw(g, left + 12, top + 46, windowWidth - 24, windowHeight - 89);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
