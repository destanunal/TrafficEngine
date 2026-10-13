package com.destan.trafficengine.client.screen;

import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.Constants;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.block.entity.TrafficLightControllerBlockEntity;
import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TECloseButton;
import com.destan.trafficengine.client.gui.components.TENumberBox;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.components.TEWorkbenchIcons;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.client.widgets.trafficlight.TrafficLightConfig;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.network.packets.cts.TrafficLightPacket;
import com.destan.trafficengine.network.packets.cts.TrafficLightSchedulePacket;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.util.Clipboard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Native TE editor. Config/NBT, packets and the shared schedule editor remain unchanged. */
public class TrafficLightConfigScreen extends TrafficEngineScreen {
    private enum Tab { GENERAL, SIGNALS, CONTROL }
    private static final class FormWidget {
        final AbstractWidget widget;
        final int y;
        FormWidget(AbstractWidget widget, int y) { this.widget = widget; this.y = y; }
    }
    private static final class FormLabel {
        final String text;
        final int y;
        FormLabel(String text, int y) { this.text = text; this.y = y; }
    }

    private final TrafficLightConfig config;
    private final Component previewLabel = text("preview_label");
    private final ResourceLocation[] signalTextures = new ResourceLocation[TrafficLightModel.maxRequiredSlots()];
    private String invalidIdLabel;
    private boolean idEdited;
    private BlockState previewState;
    private Tab tab = Tab.GENERAL;
    private final List<TEButton> chrome = new ArrayList<>();
    private final List<FormWidget> form = new ArrayList<>();
    private final List<FormLabel> labels = new ArrayList<>();
    private final List<TENumberBox> numbers = new ArrayList<>();
    private TENumberBox phaseBox;
    private TEButton done;
    private int previewX, previewY, previewWidth, previewHeight;
    private int formX, formY, formWidth, formHeight, contentHeight, scroll;
    private float previewScale;
    private int lampX, lampY;
    private int selectedSignal = -1;
    private boolean closed, rebuildRequested, draggingScrollbar;

    public TrafficLightConfigScreen(Level level, BlockPos pos) {
        super(Component.translatable("gui.trafficengine.trafficlight.title"));
        config = new TrafficLightConfig(level, pos);
    }

    private static Component text(String name) { return Component.translatable("gui.trafficengine.trafficlight." + name); }
    @Override protected void init() {
        rememberIdEdits();
        layoutWindow(470, 330);
        chrome.clear();
        form.clear();
        numbers.clear();
        previewX = left + 12;
        previewY = top + 47;
        previewWidth = Math.min(120, (windowWidth - 24) / 3);
        previewHeight = windowHeight - 89;
        formX = previewX + previewWidth + 12;
        formY = top + 80;
        formWidth = left + windowWidth - 12 - formX;
        formHeight = previewY + previewHeight - formY - 3;
        invalidIdLabel = font.plainSubstrByWidth(text("invalid_id").getString(), Math.max(60, windowWidth - 184));
        int tabWidth = formWidth / 3;
        for (Tab value : Tab.values()) {
            TEButton button = addRenderableWidget(new TEButton(formX + value.ordinal() * tabWidth, previewY,
                tabWidth - 3, 22, text("tab." + value.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                    tab = value;
                    scroll = 0;
                    chrome.forEach(c -> c.setSelected(false));
                    ((TEButton)b).setSelected(true);
                    rebuildForm();
                }));
            button.setSelected(tab == value);
            chrome.add(button);
        }
        chrome.add(addRenderableWidget(new TECloseButton(left + windowWidth - 29, top + 8, 21, 19, b -> onClose())));
        chrome.add(addRenderableWidget(new TEButton(left + windowWidth - 164, top + windowHeight - 30, 72, 20,
            Component.translatable("gui.cancel"), b -> { closed = true; super.onClose(); })));
        done = addRenderableWidget(new TEButton(left + windowWidth - 86, top + windowHeight - 30, 74, 20,
            Component.translatable("gui.done"), b -> onClose()).primary());
        chrome.add(done);
        rebuildForm();
    }

    private void label(Component text, int y) { labels.add(new FormLabel(font.plainSubstrByWidth(text.getString(), formWidth - 16), y)); }
    private <T extends AbstractWidget> T field(T widget, int y) {
        form.add(new FormWidget(addRenderableWidget(widget), y));
        contentHeight = Math.max(contentHeight, y + widget.getHeight() + 5);
        return widget;
    }
    private TEButton button(int x, int y, int width, Component title, boolean selected, net.minecraft.client.gui.components.Button.OnPress action) {
        TEButton button = field(new TEButton(x, formY + y, width, 20, title, action), y);
        button.setSelected(selected);
        button.setTooltip(Tooltip.create(title));
        return button;
    }

    private void rebuildForm() {
        rememberIdEdits();
        boolean restorePhaseFocus = phaseBox != null && getFocused() == phaseBox;
        int cursor = restorePhaseFocus ? phaseBox.getCursorPosition() : 0;
        for (FormWidget entry : form) {
            if (getFocused() == entry.widget) setFocused(null);
            removeWidget(entry.widget);
        }
        form.clear();
        labels.clear();
        helpLines.clear();
        numbers.clear();
        phaseBox = null;
        contentHeight = 0;
        rebuildRequested = false;
        previewState = ModBlocks.TRAFFIC_LIGHT.get().defaultBlockState()
            .setValue(TrafficLightBlock.FACING, Direction.NORTH).setValue(TrafficLightBlock.MODEL, config.model);
        for (int i = 0; i < config.model.getLightsCount(); i++)
            signalTextures[i] = TrafficLightTextureManager.getResourceLocation(config.icon, slotColor(i));
        switch (tab) {
            case GENERAL -> generalSettings();
            case SIGNALS -> signalSettings();
            case CONTROL -> controlSettings();
        }
        if (restorePhaseFocus && phaseBox != null) {
            setFocused(phaseBox);
            phaseBox.setCursorPosition(Math.min(cursor, phaseBox.getValue().length()));
        }
        scroll = Math.min(scroll, maxScroll());
        positionForm();
    }

    private void generalSettings() {
        label(text("timer_label"), 1);
        int typeWidth = (formWidth - 11) / TrafficLightType.values().length;
        for (TrafficLightType type : TrafficLightType.values()) {
            button(formX + 5 + type.ordinal() * typeWidth, 14, typeWidth - 3, type.getValueTranslation(), config.type == type, b -> {
                config.type = type;
                rebuildForm();
            });
        }
        label(text("model_label"), 45);
        int modelWidth = (formWidth - 11) / TrafficLightModel.values().length;
        for (TrafficLightModel model : TrafficLightModel.values()) {
            TEButton choice = button(formX + 5 + model.ordinal() * modelWidth, 58, modelWidth - 3,
                Component.literal(Integer.toString(model.getLightsCount())), config.model == model, b -> {
                    boolean expandTwoLights = config.model == TrafficLightModel.TWO_LIGHTS && model == TrafficLightModel.THREE_LIGHTS;
                    config.model = model;
                    config.useTwoLightColors();
                    if (expandTwoLights) {
                        config.colors[0] = TrafficLightColor.RED;
                        config.colors[1] = TrafficLightColor.YELLOW;
                        config.colors[2] = TrafficLightColor.GREEN;
                    }
                    selectedSignal = Math.min(selectedSignal, model.getLightsCount() - 1);
                    rebuildForm();
                });
            choice.setTooltip(Tooltip.create(model.getValueTranslation()));
        }
        label(text("icon_label"), 89);
        // Two columns leave room for names at the smallest Minecraft GUI scale.
        TrafficLightIcon[] icons = TrafficLightIcon.getAllowedForType(config.type);
        int iconWidth = (formWidth - 11) / 2;
        for (int i = 0; i < icons.length; i++) {
            TrafficLightIcon icon = icons[i];
            button(formX + 5 + i % 2 * iconWidth, 102 + i / 2 * 24, iconWidth - 3,
                icon.getValueTranslation(), config.icon == icon, b -> {
                    config.icon = icon;
                    config.useTwoLightColors();
                    rebuildForm();
                });
        }
    }

    private TrafficLightColor slotColor(int slot) {
        return slot < config.colors.length && config.colors[slot] != null ? config.colors[slot] : TrafficLightColor.NONE;
    }
    private void signalSettings() {
        for (int slot = 0; slot < config.model.getLightsCount(); slot++) {
            final int index = slot;
            int row = slot * 45;
            label(text("signal_number").copy().append(" " + (slot + 1)), row + 1);
            List<TrafficLightColor> allowed = new ArrayList<>();
            for (TrafficLightColor color : TrafficLightColor.getAllowedForType(config.type, true)) {
                if (config.icon == TrafficLightIcon.PEDESTRIAN && config.model == TrafficLightModel.TWO_LIGHTS
                        && color == TrafficLightColor.YELLOW) continue;
                allowed.add(color);
            }
            int colorWidth = (formWidth - 11) / allowed.size();
            for (int i = 0; i < allowed.size(); i++) {
                TrafficLightColor color = allowed.get(i);
                SignalButton choice = field(new SignalButton(formX + 5 + i * colorWidth, formY + row + 14,
                    colorWidth - 3, color, b -> {
                        config.colors[index] = color;
                        selectedSignal = index;
                        rebuildForm();
                    }), row + 14);
                choice.setSelected(slotColor(slot) == color);
            }
        }
    }

    private boolean crossing() { return config.icon == TrafficLightIcon.PEDESTRIAN || config.icon == TrafficLightIcon.BIKE; }
    private void controlSettings() {
        label(text("control_label"), 1);
        TrafficLightControlType[] modes = TrafficLightControlType.values();
        button(formX + 5, 14, formWidth - 13, config.controlType.getValueTranslation(), false, b -> {
            config.controlType = modes[(config.controlType.ordinal() + 1) % modes.length];
            scroll = 0;
            rebuildForm();
        }).setTooltip(Tooltip.create(config.controlType.getValueDescriptionTranslation()));
        switch (config.controlType) {
            case STATIC -> staticSettings();
            case OWN_SCHEDULE -> scheduleSettings();
            case REMOTE -> remoteSettings();
        }
    }

    private void staticSettings() {
        label(text("set_enabled_colors"), 45);
        TrafficLightColor[] colors = TrafficLightColor.getAllowedForType(config.type, false);
        int colorWidth = (formWidth - 11) / colors.length;
        for (int i = 0; i < colors.length; i++) {
            TrafficLightColor color = colors[i];
            SignalButton choice = field(new SignalButton(formX + 5 + i * colorWidth, formY + 58, colorWidth - 3, color, b -> {
                if (!config.enabledColors.remove(color)) config.enabledColors.add(color);
                ((TEButton)b).setSelected(config.enabledColors.contains(color));
            }), 58);
            choice.setSelected(config.enabledColors.contains(color));
        }
    }

    private void scheduleSettings() {
        button(formX + 5, 45, formWidth - 13, text("edit_schedule"), false,
            b -> minecraft.setScreen(new TrafficLightScheduleEditor(this, config.level, config.blockPos)));
        int half = (formWidth - 11) / 2;
        button(formX + 5, 71, half - 3, Constants.textCopy, false, b -> {
            if (config.level.getBlockEntity(config.blockPos) instanceof TrafficLightControllerBlockEntity controller) {
                Clipboard.put(TrafficLightSchedule.class, controller.getScheduleForEditing());
            } else if (config.level.getBlockEntity(config.blockPos) instanceof TrafficLightBlockEntity light) {
                Clipboard.put(TrafficLightSchedule.class, light.getSchedule());
            }
        });
        button(formX + 5 + half, 71, half - 3, Constants.textPaste, false, b ->
            Clipboard.get(TrafficLightSchedule.class).ifPresent(schedule ->
                ModNetworkManager.UPDATE_TRAFFIC_LIGHT_SCHEDULE.send(NetworkDirection.toServer(),
                    new TrafficLightSchedulePacket(config.blockPos, List.of(schedule)))));
        button(formX + 5, 97, formWidth - 13, scheduleStatus(), config.scheduleEnabled, b -> {
            config.scheduleEnabled = !config.scheduleEnabled;
            b.setMessage(scheduleStatus());
            ((TEButton)b).setSelected(config.scheduleEnabled);
        });
    }
    private Component scheduleStatus() {
        return text("schedule_status").copy().append(": ").append(config.scheduleEnabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private void remoteSettings() {
        label(text(config.icon == TrafficLightIcon.PEDESTRIAN ? "pedestrian_stop_id"
            : config.icon == TrafficLightIcon.BIKE ? "bicycle_stop_id" : "set_phase_id"), 45);
        phaseBox = field(new TENumberBox(font, formX + 5, formY + 58, formWidth - 13, config.phaseId,
            text("set_phase_id"), TrafficLightSchedule::isValidPhaseId, value -> {
                config.phaseId = value;
                if (config.additionalPedestrianStopIds.remove(value)) rebuildRequested = true;
            }), 58);
        numbers.add(phaseBox);
        int y = 85;
        if (crossing()) {
            for (int savedId : config.additionalPedestrianStopIds.stream().sorted().toList()) {
                int[] currentId = {savedId};
                label(text(config.icon == TrafficLightIcon.BIKE ? "additional_bicycle_stop_id" : "additional_stop_id"), y);
                TENumberBox extra = field(new TENumberBox(font, formX + 5, formY + y + 13, formWidth - 55, savedId,
                    text("additional_stop_id"), value -> TrafficLightSchedule.isValidPhaseId(value) && value != config.phaseId
                        && (value == currentId[0] || !config.additionalPedestrianStopIds.contains(value)), value -> {
                            config.additionalPedestrianStopIds.remove(currentId[0]);
                            config.additionalPedestrianStopIds.add(value);
                            currentId[0] = value;
                        }), y + 13);
                numbers.add(extra);
                button(formX + formWidth - 45, y + 13, 36, text("delete_stop_id_button"), false, b -> {
                    config.additionalPedestrianStopIds.remove(currentId[0]);
                    rebuildForm();
                }).setTooltip(Tooltip.create(text("remove_stop_id")));
                y += 40;
            }
            if (config.additionalPedestrianStopIds.size() < 8) {
                button(formX + 5, y, formWidth - 13,
                    text(config.icon == TrafficLightIcon.BIKE ? "add_bicycle_stop_id" : "add_stop_id"), false, b -> {
                        int next = 1;
                        while (next == config.phaseId || config.additionalPedestrianStopIds.contains(next)) next++;
                        config.additionalPedestrianStopIds.add(next);
                        rebuildForm();
                        scroll = Math.max(0, 85 + (config.additionalPedestrianStopIds.size() - 1) * 40 + 35 - formHeight);
                        scroll = Math.min(scroll, maxScroll());
                        positionForm();
                    });
                y += 29;
            }
        }
        Component help = text(config.icon == TrafficLightIcon.PEDESTRIAN ? "pedestrian_stop_id.description"
            : config.icon == TrafficLightIcon.BIKE ? "bicycle_stop_id.description" : "set_phase_id.description");
        for (var line : font.split(help, formWidth - 16)) {
            // Keep wrapped help part of the scrollable form as well.
            helpLines.add(new HelpLine(line, y));
            y += font.lineHeight + 2;
        }
        contentHeight = Math.max(contentHeight, y + 5);
    }
    private static final class HelpLine {
        final net.minecraft.util.FormattedCharSequence text;
        final int y;
        HelpLine(net.minecraft.util.FormattedCharSequence text, int y) { this.text = text; this.y = y; }
    }
    private final List<HelpLine> helpLines = new ArrayList<>();

    private int maxScroll() { return Math.max(0, contentHeight - formHeight); }
    private void positionForm() {
        for (FormWidget entry : form) {
            AbstractWidget widget = entry.widget;
            widget.setY(formY + entry.y - scroll);
            widget.visible = widget.getY() >= formY && widget.getY() + widget.getHeight() <= formY + formHeight;
            if (!widget.visible && getFocused() == widget) setFocused(null);
        }
    }

    private void rememberIdEdits() {
        idEdited |= numbers.stream().anyMatch(TENumberBox::hasBeenEdited);
    }
    private boolean showIdWarning() {
        return tab == Tab.CONTROL && config.controlType == TrafficLightControlType.REMOTE
            && (idEdited || numbers.stream().anyMatch(TENumberBox::hasBeenEdited)) && !validNumbers();
    }
    private boolean validNumbers() {
        if (config.controlType == TrafficLightControlType.REMOTE && !validIds()) return false;
        for (TENumberBox box : numbers) if (!box.isValid()) return false;
        return true;
    }
    private boolean validIds() {
        if (!TrafficLightSchedule.isValidPhaseId(config.phaseId)) return false;
        for (int id : config.additionalPedestrianStopIds) if (!TrafficLightSchedule.isValidPhaseId(id) || id == config.phaseId) return false;
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
        if (insideForm(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int)(delta * 24)));
            positionForm();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, delta);
    }
    private boolean insideForm(double x, double y) { return x >= formX && x < formX + formWidth && y >= formY && y < formY + formHeight; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            int step = Math.max(20, formHeight - 20);
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? step : -step)));
            positionForm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && insideForm(mouseX, mouseY) && mouseX >= formX + formWidth - 5) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        if (button == 0 && mouseX >= previewX && mouseX < previewX + previewWidth && mouseY >= previewY && mouseY < previewY + previewHeight) {
            selectedSignal = -1;
            for (int i = 0; i < config.model.getLightsCount(); i++) {
                int sy = lampY + Math.round((1.5f + i * 5) * previewScale);
                if (mouseX >= lampX + 6 * previewScale && mouseX < lampX + 10 * previewScale
                        && mouseY >= sy && mouseY < sy + 4 * previewScale) { selectedSignal = i; break; }
            }
            tab = selectedSignal < 0 ? Tab.GENERAL : Tab.SIGNALS;
            scroll = selectedSignal < 0 ? 0 : Math.max(0, selectedSignal * 45 - formHeight / 2);
            // Refresh the selected tab along with its controls.
            for (int i = 0; i < 3; i++) chrome.get(i).setSelected(i == tab.ordinal());
            rebuildForm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    private void scrollTo(double y) {
        int thumb = Math.max(16, formHeight * formHeight / Math.max(1, contentHeight));
        scroll = Math.max(0, Math.min(maxScroll(), (int)Math.round((y - formY - thumb / 2.0)
            / Math.max(1, formHeight - thumb) * maxScroll())));
        positionForm();
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScrollbar) { scrollTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void onClose() {
        if (closed) return;
        if (config.controlType == TrafficLightControlType.REMOTE && !validIds() && tab != Tab.CONTROL) {
            tab = Tab.CONTROL;
            scroll = 0;
            for (int i = 0; i < 3; i++) chrome.get(i).setSelected(i == tab.ordinal());
            rebuildForm();
        }
        for (TENumberBox box : numbers) if (!box.isValid()) {
            for (FormWidget entry : form) if (entry.widget == box) scroll = Math.min(maxScroll(), entry.y);
            positionForm();
            setFocused(box);
            return;
        }
        if (!validNumbers()) return;
        closed = true;
        int maxSlots = TrafficLightModel.maxRequiredSlots();
        TrafficLightColor[] safeColors = new TrafficLightColor[maxSlots];
        for (int i = 0; i < maxSlots; i++) safeColors[i] = slotColor(i);
        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_PACKET.send(NetworkDirection.toServer(), new TrafficLightPacket(
            config.blockPos, config.enabledColors, config.type, config.model, config.icon, config.controlType,
            safeColors, config.phaseId, config.additionalPedestrianStopIds, config.scheduleEnabled));
        super.onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWindow(g);
        TEPanel.draw(g, previewX, previewY, previewWidth, previewHeight);
        TEPanel.draw(g, formX, formY - 3, formWidth, formHeight + 6);
        g.drawString(font, previewLabel, previewX + 8, previewY + 8, TEColors.MUTED, false);
        renderPreview(g);
        g.enableScissor(formX + 1, formY, formX + formWidth - 1, formY + formHeight);
        for (FormLabel label : labels) {
            g.drawString(font, label.text, formX + 5,
                formY + label.y - scroll, TEColors.MUTED, false);
        }
        for (HelpLine line : helpLines) g.drawString(font, line.text, formX + 5, formY + line.y - scroll, TEColors.MUTED, false);
        for (FormWidget entry : form) entry.widget.render(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        if (maxScroll() > 0) {
            int thumb = Math.max(16, formHeight * formHeight / contentHeight);
            int sy = formY + (formHeight - thumb) * scroll / maxScroll();
            g.fill(formX + formWidth - 4, formY, formX + formWidth - 2, formY + formHeight, TEColors.FIELD);
            g.fill(formX + formWidth - 4, sy, formX + formWidth - 2, sy + thumb, TEColors.ACCENT);
        }
        for (TEButton widget : chrome) widget.render(g, mouseX, mouseY, partialTick);
        if (showIdWarning()) {
            g.drawString(font, invalidIdLabel, left + 12,
                top + windowHeight - 23, 0xFFE86A63, false);
        }
    }

    private void renderPreview(GuiGraphics g) {
        float modelHeight = Math.max(16, config.model.getTotalHitboxHeight());
        previewScale = Math.min(6, Math.min((previewHeight - 35) / modelHeight, (previewWidth - 20) / 16f));
        lampX = previewX + previewWidth / 2 - Math.round(8 * previewScale);
        lampY = previewY + 24 + Math.round((previewHeight - 30 - modelHeight * previewScale) / 2);
        g.flush();
        Lighting.setupForFlatItems();
        g.pose().pushPose();
        g.pose().translate(lampX, lampY + 16 * previewScale, 100);
        g.pose().scale(16 * previewScale, -16 * previewScale, -16 * previewScale);
        minecraft.getBlockRenderer().renderSingleBlock(previewState, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        g.pose().popPose();
        Lighting.setupFor3DItems();
        // Overlay the same signal textures at their model coordinates, in front of the housing.
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        RenderSystem.enableBlend();
        for (int i = 0; i < config.model.getLightsCount(); i++) {
            int sx = lampX + Math.round(6 * previewScale);
            int sy = lampY + Math.round((1.5f + i * 5) * previewScale);
            int size = Math.round(4 * previewScale);
            g.blit(signalTextures[i], sx, sy, size, size, 0, 0, 16, 16, 16, 16);
            if (selectedSignal == i) {
                TEPanel.outline(g, sx - 2, sy - 2, size + 4, size + 4, TEColors.SELECTION_BORDER);
                TEPanel.outline(g, sx - 1, sy - 1, size + 2, size + 2, TEColors.SELECTION_BORDER);
            }
        }
        g.flush();
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private final class SignalButton extends TEButton {
        private final ResourceLocation texture;
        SignalButton(int x, int y, int width, TrafficLightColor color, OnPress action) {
            super(x, y, width, 24, color.getValueTranslation(), action);
            this.texture = TrafficLightTextureManager.getResourceLocation(config.icon, color);
            setTooltip(Tooltip.create(color.getValueTranslation().copy().append("\n").append(color.getValueDescriptionTranslation())));
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean selected = active && isSelected(), hover = active && isHoveredOrFocused();
            int border = selected ? TEColors.SELECTION_BORDER : hover ? 0xFF8FAAC4 : TEColors.BORDER;
            int inset = selected ? 2 : 1;
            rounded(g, getX(), getY(), width, height, border);
            rounded(g, getX() + inset, getY() + inset, width - inset * 2, height - inset * 2,
                selected ? 0xFF244355 : hover ? 0xFF394755 : TEColors.FIELD);
            renderLabel(g, TEColors.TEXT);
            if (selected && width >= 50) {
                int x = getX() + width - 21, y = getY() + 5;
                rounded(g, x, y, 14, 14, TEColors.SELECTED);
                TEWorkbenchIcons.draw(g, ModGuiIcons.CHECK, x - 1, y - 1, TEColors.TEXT);
            } else if (selected) {
                g.fill(getX() + 7, getY() + height - 3, getX() + width - 7, getY() + height - 2, TEColors.SELECTION_BORDER);
            }
        }
        private void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x + 3, y, x + w - 3, y + h, color);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
            g.fill(x, y + 3, x + w, y + h - 3, color);
        }
        @Override protected void renderLabel(GuiGraphics g, int color) {
            // Draw the cached icon while retaining the accessible button message.
            int size = Math.min(16, width - 6);
            RenderSystem.enableBlend();
            g.blit(texture, getX() + (width - size) / 2,
                getY() + (height - size) / 2, size, size, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }
    }
}
