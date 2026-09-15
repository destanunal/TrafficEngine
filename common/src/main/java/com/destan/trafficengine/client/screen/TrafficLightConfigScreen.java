package com.destan.trafficengine.client.screen;

import java.util.List;

import de.mrjulsen.mcdragonlib.DragonLib;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.Direction;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils.TextureFillMode;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.events.EventListenerId;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.client.TrafficLightTextureManager.TrafficLightTextureKey;
import com.destan.trafficengine.client.widgets.trafficlight.TrafficLightConfig;
import com.destan.trafficengine.client.widgets.trafficlight.TrafficLightControlSettings;
import com.destan.trafficengine.client.widgets.trafficlight.TrafficLightGeneralSettings;
import com.destan.trafficengine.client.widgets.trafficlight.TrafficLightSignalSettings;
import com.destan.trafficengine.network.packets.cts.TrafficLightPacket;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightConfigScreen extends DLWindow {

    public static boolean hide3DBlock = false;
    public static final int WINDOW_WIDTH = 256;
    public static final int WINDOW_HEIGHT = 245;

    private final TrafficLightConfig config;
    private final Component title = TextUtils.translate("gui.trafficengine.trafficlight.title");

    public TrafficLightConfigScreen(DLWindowManager manager, Level level, BlockPos pos) {
        super(manager);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        windowSpawnPosition.set(WindowPosition.CENTER);

        this.config = new TrafficLightConfig(level, pos);

        int maxSlots = TrafficLightModel.maxRequiredSlots();
        if (this.config.colors.length < maxSlots) {
            TrafficLightColor[] newArr = new TrafficLightColor[maxSlots];
            System.arraycopy(this.config.colors, 0, newArr, 0, this.config.colors.length);
            for (int i = this.config.colors.length; i < maxSlots; i++) {
                newArr[i] = TrafficLightColor.NONE;
            }
            this.config.colors = newArr;
        }

        DLPanel topContainer = addComponent(new DLPanel(0, 0, 1, 1));
        FlowLayout settingsLayout = new FlowLayout();
        settingsLayout.fillCrossAxis.set(false);
        settingsLayout.flowDirection.set(Direction.HORIZONTAL);
        settingsLayout.horizontalGap.set(5);
        settingsLayout.wrap.set(false);
        topContainer.layout.set(settingsLayout);

        // Genişlik 185 piksele çıkarıldı
        DLPanel optionsPanel = new DLPanel(0, 0, 185, 100);

        TrafficLightPanel trafficLightPanel = new TrafficLightPanel(0, 0, config, optionsPanel);

        topContainer.addComponent(trafficLightPanel);
        topContainer.addComponent(optionsPanel);

        Runnable updateTopContainer = () -> {
            int neededH = Math.max(trafficLightPanel.height(), optionsPanel.height());
            if (topContainer.height() != neededH) {
                topContainer.setHeight(neededH);
            }
        };

        trafficLightPanel.addEventListener(DLGuiStandardEvents.ComponentPosAndSizeChanged.class, (s, e) -> {
            if (e.heightChanged()) updateTopContainer.run();
            return false;
        });

        optionsPanel.addEventListener(DLGuiStandardEvents.ComponentPosAndSizeChanged.class, (s, e) -> {
            if (e.heightChanged()) updateTopContainer.run();
            return false;
        });

        updateTopContainer.run();

        addComponent(new TrafficLightControlSettings(0, 0, 1, 100, config));

        FlowLayout layout = new FlowLayout();
        layout.fillCrossAxis.set(true);
        layout.flowDirection.set(Direction.VERTICAL);
        layout.verticalGap.set(5);
        layout.wrap.set(false);
        layout.padding.set(new Padding(5, 0, 0, 0));

        addEventListener(DLGuiStandardEvents.ComponentLayoutUpdatedEvent.class, (s, e) -> {
            int newH = 20 + e.layoutResult().contentHeight();
            if (height() != newH) {
                setHeight(newH);
            }
            return false;
        });

        this.layout.set(layout);
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        GuiUtils.drawString(graphics, graphics.defaultFont(), width() / 2, 0, title, DragonLib.VANILLA_BUTTON_ACTIVE_FONT_COLOR, ETextAlignment.CENTER, true);
    }

    @Override
    public void close() throws Exception {
        int maxSlots = TrafficLightModel.maxRequiredSlots();
        TrafficLightColor[] safeColors = new TrafficLightColor[maxSlots];
        for (int i = 0; i < maxSlots; i++) {
            safeColors[i] = (i < config.colors.length && config.colors[i] != null) ? config.colors[i] : TrafficLightColor.NONE;
        }

        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_PACKET.send(NetworkDirection.toServer(), new TrafficLightPacket(
                config.blockPos,
                config.enabledColors,
                config.type,
                config.model,
                config.icon,
                config.controlType,
                safeColors,
                config.phaseId,
                config.scheduleEnabled
        ));
    }

    private static class TrafficLightPanel extends DLGuiComponent {

        private static final int SCALE = 6;
        private static final int OFFSET = 1;
        private static final float TRAFFIC_LIGHT_WIDTH = 8;
        private static final int TRAFFIC_LIGHT_SIGNAL_SIZE = 4;

        private final DLPanel optionsPanel;
        private final TrafficLightConfig config;
        private final EventListenerId eventId;

        private static final Component textEditTrafficLight = TextUtils.translate("gui.trafficengine.trafficlight.edit_traffic_light");

        public TrafficLightPanel(int x, int y, TrafficLightConfig config, DLPanel optionsPanel) {
            super(x, y, 1, 1);

            this.config = config;
            this.optionsPanel = optionsPanel;

            tooltip.set(new DLTooltip(List.of(textEditTrafficLight), 200));

            addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
                openGeneralSettings();
                return false;
            });

            eventId = config.addEventListener(TrafficLightConfig.UpdateEvent.class, (s, e) -> {
                update();
                return false;
            });
            update();
        }

        private void openGeneralSettings() {
            optionsPanel.clearComponents();
            TrafficLightGeneralSettings settings = optionsPanel.addComponent(new TrafficLightGeneralSettings(0, 0, 185, 100, config));
            settings.setY(10);
            optionsPanel.setWidth(185);
            optionsPanel.setHeight(settings.height() + 15);
        }

        @Override
        public void close() throws Exception {
            config.removeEventListener(TrafficLightConfig.UpdateEvent.class, eventId);
        }

        private float getDynamicHeight() {
            int count = config.model.getLightsCount();
            if (count == 4) return 22.5f;
            if (count == 3) return 16.5f;
            if (count == 2) return 11.5f;
            return 6.5f;
        }

        @Override
        public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
            if (TrafficLightConfigScreen.hide3DBlock) return;

            BlockState state = ModBlocks.TRAFFIC_LIGHT.get().defaultBlockState();
            graphics.poseStack().pushPose();
            graphics.poseStack().translate(0, 0, 150);

            GuiUtils.renderBlockState(graphics, (int)(width() / 2 - TRAFFIC_LIGHT_WIDTH * SCALE), SCALE, SCALE, state.setValue(TrafficLightBlock.MODEL, config.model), RenderType.solid(), LightTexture.FULL_BRIGHT);

            if (isSelected()) {
                int realHeight = (int)((getDynamicHeight() + OFFSET * 2) * SCALE);
                GuiUtils.drawBox(graphics, 0, 0, width(), realHeight, DLColor.fromInt(0x40FFFFFF), DLColor.WHITE);
            }

            graphics.poseStack().popPose();
        }

        public void update() {
            clearComponents();

            float currentHeight = getDynamicHeight();
            int targetW = (int)((TRAFFIC_LIGHT_WIDTH + OFFSET * 2) * SCALE);
            int targetH = (int)((currentHeight + OFFSET * 2) * SCALE);

            if (width() != targetW || height() != targetH) {
                setSize(targetW, targetH);
            }

            float x = 2 + OFFSET;
            float y = 1.5f + OFFSET;

            int limit = Math.min(config.model.getLightsCount(), 4);

            for (byte i = 0; i < limit; i++) {
                final byte k = i;
                final float currentY = y + (i * 5);
                int buttonAbsoluteY = (int)(currentY * SCALE);

                TrafficLightSignalButton signal = new TrafficLightSignalButton((int)(x * SCALE), buttonAbsoluteY, SCALE * TRAFFIC_LIGHT_SIGNAL_SIZE, SCALE * TRAFFIC_LIGHT_SIGNAL_SIZE, config, i);
                signal.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
                    optionsPanel.clearComponents();
                    TrafficLightSignalSettings settings = optionsPanel.addComponent(new TrafficLightSignalSettings(0, 0, 185, 100, k, config));
                    settings.setY(Math.max(0, buttonAbsoluteY + (TRAFFIC_LIGHT_SIGNAL_SIZE * SCALE / 2) - settings.height() / 2));
                    optionsPanel.setWidth(185);
                    optionsPanel.setHeight(settings.y() + settings.height() + 10);
                    return false;
                });
                addComponent(signal);
            }

            if (optionsPanel.getComponents().isEmpty()) {
                openGeneralSettings();
            } else {
                DLGuiComponent activeSettings = optionsPanel.getComponents().get(0);
                if (activeSettings instanceof TrafficLightGeneralSettings) {
                    activeSettings.setY(10);
                    optionsPanel.setHeight(activeSettings.height() + 15);
                }
            }
        }
    }

    private static class TrafficLightSignalButton extends DLGuiComponent {

        private final TrafficLightConfig config;
        private final byte signalId;
        private final String keyAreaTrafficLightSignal = "gui.trafficengine.trafficlight.edit_signal_";

        public TrafficLightSignalButton(int x, int y, int w, int h, TrafficLightConfig config, byte signalId) {
            super(x, y, w, h);
            this.config = config;
            this.signalId = signalId;
            tooltip.set(new DLTooltip(List.of(TextUtils.translate(keyAreaTrafficLightSignal + signalId)), 200));
        }

        @Override
        public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
            if (TrafficLightConfigScreen.hide3DBlock) return;

            graphics.poseStack().pushPose();
            graphics.poseStack().translate(0, 0, 160);

            TrafficLightColor c = (signalId < config.colors.length && config.colors[signalId] != null) ? config.colors[signalId] : TrafficLightColor.NONE;
            GuiUtils.drawTexture(TrafficLightTextureManager.getResourceLocation(new TrafficLightTextureKey(config.icon, c)), graphics, 0, 0, width(), height(), 0, 0, 16, 16, TextureFillMode.STRETCH, 16, 16);

            if (isSelected()) {
                GuiUtils.drawBox(graphics, 0, 0, width(), height(), DLColor.fromInt(0x40FFFFFF), DLColor.WHITE);
            }
            graphics.poseStack().popPose();
        }
    }
}