package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
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
import com.destan.trafficengine.client.gui.components.TESignalIcons;
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
    private final List<TEButton> previewTools = new ArrayList<>(4);
    private int previewTint = -1;
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

    public TrafficLightConfigScreen(Level level,BlockPos pos){this(level,pos,0);}
    public TrafficLightConfigScreen(Level level, BlockPos pos, int face) {
        super(Component.translatable("gui.trafficengine.trafficlight.title").append(level.getBlockState(pos).getBlock() instanceof com.destan.trafficengine.block.DoubleTrafficLightBlock?Component.literal(" - ").append(Component.translatable(face==1?"gui.trafficengine.signal.back":"gui.trafficengine.signal.front")):Component.empty()));
        config = new TrafficLightConfig(level, pos, face);
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

    public int getSignalFace(){return config.signalFace;}
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
        for(TEButton tool:previewTools){if(getFocused()==tool)setFocused(null);removeWidget(tool);}
        previewTools.clear();
        labels.clear();
        helpLines.clear();
        numbers.clear();
        phaseBox = null;
        contentHeight = 0;
        rebuildRequested = false;
        previewState = ModBlocks.TRAFFIC_LIGHT.get().defaultBlockState()
            .setValue(TrafficLightBlock.FACING, Direction.NORTH).setValue(TrafficLightBlock.MODEL, config.model);
        for (int i = 0; i < config.model.getLightsCount(); i++)
            signalTextures[i] = TrafficLightTextureManager.getRoundResourceLocation(config.signalSymbols.icon(i,config.icon), slotColor(i));
        previewTint=-1;
        if(com.destan.trafficengine.util.SignalFaces.get(config.level,config.blockPos,config.signalFace) instanceof com.destan.trafficengine.block.data.IColorBlockEntity painted
                && painted.getColor()!=com.destan.trafficengine.data.PaintColor.NONE)
            previewTint=painted.getColor().getTextureColor().getAsARGB();
        layoutPreview();
        switch (tab) {
            case GENERAL -> generalSettings();
            case SIGNALS -> signalSettings();
            case CONTROL -> controlSettings();
        }
        if (restorePhaseFocus && phaseBox != null) {
            setFocused(phaseBox);
            phaseBox.setCursorPosition(Math.min(cursor, phaseBox.getValue().length()));
        }
        if(tab==Tab.SIGNALS)addSymbolTools();
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
        TrafficLightIcon[] icons = TrafficLightIcon.getAllowedForType(config.type);
        int columns = Math.max(1, (formWidth - 10 + 4) / 30);
        for (int i = 0; i < icons.length; i++) {
            TrafficLightIcon icon = icons[i];
            int y = 102 + i / columns * 30;
            IconButton choice = field(new IconButton(formX + 5 + i % columns * 30, formY + y, icon, b -> {
                config.icon = icon;
                config.signalSymbols = com.destan.trafficengine.data.SignalSymbols.DEFAULT;
                config.customMask = com.destan.trafficengine.data.PixelPattern.EMPTY;
                config.useTwoLightColors();
                rebuildForm();
            }), y);
            choice.setSelected(config.customMask.isEmpty() && config.icon == icon);
        }
        Component addTitle = Component.translatable("gui.trafficengine.stencil.add_icon");
        int addWidth = Math.min(formWidth - 10, font.width(addTitle) + 34);
        int addX = 5 + icons.length % columns * 30;
        int addY = 102 + icons.length / columns * 30;
        // A labelled drawing action follows the presets, with a gap and wrapping when needed.
        if (icons.length % columns != 0) {
            addX += 8;
            if (addX + addWidth > formWidth - 5) {
                addX = 5;
                addY += 30;
            }
        }
        CustomIconButton add = field(new CustomIconButton(formX + addX, formY + addY, addWidth, addTitle), addY);
        add.setSelected(!config.customMask.isEmpty());
    }

    private TrafficLightColor slotColor(int slot) {
        return slot < config.colors.length && config.colors[slot] != null ? config.colors[slot] : TrafficLightColor.NONE;
    }
    private void signalSettings() {
        for (int slot = 0; slot < config.model.getLightsCount(); slot++) {
            final int index = slot;
            int rowStep=Math.min(62,Math.max(28,(formHeight-4)/config.model.getLightsCount()));
            int row = slot * rowStep;
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
                    colorWidth - 3, index, color, b -> {
                        config.colors[index] = color;
                        selectedSignal = index;
                        rebuildForm();
                    }), row + 14);
                choice.fitHeight(Math.max(10,Math.min(24,rowStep-18)));
                choice.setSelected(slotColor(slot) == color);
            }


        }
    }

    private void editSignalSymbol(int slot){
        selectedSignal=slot;
        minecraft.setScreen(new SignalSymbolScreen(this,slot,config.signalSymbols,value->{config.signalSymbols=value;rebuildRequested=true;}));
    }
    private void addSymbolTools(){
        for(int i=0;i<config.model.getLightsCount();i++){
            final int slot=i;
            int x=lampX+Math.round(4*previewScale)-24;
            int y=lampY+Math.round((3.5F+i*5)*previewScale)-10;
            Component title=Component.translatable("gui.trafficengine.stencil.add_drawing");
            TEButton pencil=addRenderableWidget(new TEButton(x,y,20,20,title,b->editSignalSymbol(slot)){
                @Override protected void renderLabel(GuiGraphics g,int color){TEWorkbenchIcons.draw(g,ModGuiIcons.EDIT,getX()+2,getY()+2,color);}
            });
            pencil.setTooltip(Tooltip.create(title));previewTools.add(pencil);
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
            if (com.destan.trafficengine.util.SignalFaces.get(config.level,config.blockPos,config.signalFace) instanceof TrafficLightControllerBlockEntity controller) {
                Clipboard.put(TrafficLightSchedule.class, controller.getScheduleForEditing());
            } else if (com.destan.trafficengine.util.SignalFaces.get(config.level,config.blockPos,config.signalFace) instanceof TrafficLightBlockEntity light) {
                Clipboard.put(TrafficLightSchedule.class, light.getSchedule());
            }
        });
        button(formX + 5 + half, 71, half - 3, Constants.textPaste, false, b ->
            Clipboard.get(TrafficLightSchedule.class).ifPresent(schedule ->
                ModNetworkManager.UPDATE_TRAFFIC_LIGHT_SCHEDULE.send(NetworkDirection.toServer(),
                    new TrafficLightSchedulePacket(config.blockPos, List.of(schedule)).withFace(config.signalFace))));
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
        for(TEButton tool:previewTools)if(tool.isMouseOver(mouseX,mouseY))return tool.mouseClicked(mouseX,mouseY,button);
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
            scroll = 0;
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
            safeColors, config.phaseId, config.additionalPedestrianStopIds, config.scheduleEnabled).withMask(config.customMask).withSymbols(config.signalSymbols).withFace(config.signalFace));
        super.onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWindow(g);
        TEPanel.draw(g, previewX, previewY, previewWidth, previewHeight);
        TEPanel.draw(g, formX, formY - 3, formWidth, formHeight + 6);
        TEText.drawString(g, font, previewLabel, previewX + 8, previewY + 8, TEColors.MUTED, false);
        renderPreview(g);
        for(TEButton tool:previewTools)tool.render(g,mouseX,mouseY,partialTick);
        g.enableScissor(formX + 1, formY, formX + formWidth - 1, formY + formHeight);
        for (FormLabel label : labels) {
            TEText.drawString(g, font, label.text, formX + 5,
                formY + label.y - scroll, TEColors.MUTED, false);
        }
        for (HelpLine line : helpLines) TEText.drawString(g, font, line.text, formX + 5, formY + line.y - scroll, TEColors.MUTED, false);
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
            TEText.drawString(g, font, invalidIdLabel, left + 12,
                top + windowHeight - 23, 0xFFE86A63, false);
        }
    }

    private void layoutPreview() {
        float modelHeight = Math.max(16, config.model.getTotalHitboxHeight());
        previewScale = Math.min(6, Math.min((previewHeight - 35) / modelHeight, (previewWidth - 20) / 16f));
        lampX = previewX + previewWidth / 2 - Math.round(8 * previewScale);
        lampY = previewY + 24 + Math.round((previewHeight - 30 - modelHeight * previewScale) / 2);
    }
    private void renderPreview(GuiGraphics g) {
        g.flush();
        Lighting.setupForEntityInInventory();
        g.pose().pushPose();
        g.pose().translate(lampX, lampY + 16 * previewScale, 100);
        g.pose().scale(16 * previewScale, -16 * previewScale, -16 * previewScale);
        var renderer=minecraft.getBlockRenderer();
        renderer.getModelRenderer().renderModel(g.pose().last(),
            g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),
            previewState,renderer.getBlockModel(previewState),(previewTint>>>16&255)/255F,(previewTint>>>8&255)/255F,(previewTint&255)/255F,
            LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
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
            var mask=config.signalSymbols.mask(i,config.customMask);
            if (mask.isEmpty()) g.blit(signalTextures[i], sx, sy, size, size, 0, 0, 16, 16, 16, 16);
            else {
                g.fill(sx, sy, sx + size, sy + size, 0xFF11151B);
                TrafficLightColor color = slotColor(i);
                int tint = color == TrafficLightColor.RED ? 0xFFFF2020 : color == TrafficLightColor.YELLOW ? 0xFFFFCF20 : color == TrafficLightColor.GREEN ? 0xFF20FF40 : -1;
                if (color != TrafficLightColor.NONE) for (var rect : mask.rectangles())
                    g.fill(sx + rect.x() * size / 16, sy + rect.y() * size / 16, sx + (rect.x() + rect.width()) * size / 16, sy + (rect.y() + rect.height()) * size / 16, tint);
            }
            if (selectedSignal == i) {
                TEPanel.outline(g, sx - 2, sy - 2, size + 4, size + 4, TEColors.SELECTION_BORDER);
                TEPanel.outline(g, sx - 1, sy - 1, size + 2, size + 2, TEColors.SELECTION_BORDER);
            }
        }
        g.flush();
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private final class IconButton extends TEButton {
        private final TrafficLightIcon icon;
        IconButton(int x, int y, TrafficLightIcon icon, OnPress action) {
            super(x, y, 26, 26, icon.getValueTranslation(), action);
            this.icon = icon;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void renderLabel(GuiGraphics g, int color) {
            TESignalIcons.draw(g, icon, getX() + 5, getY() + 5, color);
        }
    }
    private final class CustomIconButton extends TEButton {
        CustomIconButton(int x, int y, int width, Component title) {
            super(x, y, width, 22, title, b ->
                minecraft.setScreen(new PixelPatternScreen(TrafficLightConfigScreen.this, config.customMask,
                    value -> { config.customMask = value; config.signalSymbols=com.destan.trafficengine.data.SignalSymbols.DEFAULT; rebuildRequested = true; })));
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void renderLabel(GuiGraphics g, int color) {
            float scale = TEText.layoutScale(g);
            int textWidth = Math.round(TEText.width(font,getMessage()) * scale);
            if (width < textWidth + 34) {
                super.renderLabel(g, color);
                return;
            }
            int x = getX() + (width - 22 - textWidth) / 2;
            TEWorkbenchIcons.draw(g, ModGuiIcons.EDIT, x, getY() + (height - 16) / 2, color);
            TEText.drawString(g, font, getMessage(), x + 22,
                TEText.centeredY(g, font, getMessage(), getY(), height), color, false);
        }
    }

    private final class SignalButton extends TEButton {
        private final ResourceLocation texture;
        private final com.destan.trafficengine.data.PixelPattern symbolMask;
        private final int symbolTint;
        private final boolean symbolOn;
        void fitHeight(int value){height=value;}
        SignalButton(int x,int y,int width,TrafficLightColor color,OnPress action){this(x,y,width,-1,color,action);}
        SignalButton(int x, int y, int width, int slot, TrafficLightColor color, OnPress action) {
            super(x, y, width, 24, color.getValueTranslation(), action);
            TrafficLightIcon icon=slot<0?config.icon:config.signalSymbols.icon(slot,config.icon);
            symbolMask=slot<0?config.customMask:config.signalSymbols.mask(slot,config.customMask);
            symbolOn=color!=TrafficLightColor.NONE;
            symbolTint=color==TrafficLightColor.RED?0xFFFF2020:color==TrafficLightColor.YELLOW?0xFFFFCF20:color==TrafficLightColor.GREEN?0xFF20FF40:-1;
            this.texture = TrafficLightTextureManager.getResourceLocation(symbolMask.isEmpty()?icon:TrafficLightIcon.NONE,
                symbolMask.isEmpty()?color:TrafficLightColor.NONE);
            setTooltip(Tooltip.create(color.getValueTranslation().copy().append("\n").append(color.getValueDescriptionTranslation())));
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean selected = active && isSelected(), hover = active && isHoveredOrFocused();
            int border = selected ? TEColors.SELECTION_BORDER : hover ? TEColors.SELECTION_BORDER : TEColors.BORDER;
            int inset = selected ? 2 : 1;
            rounded(g, getX(), getY(), width, height, border);
            rounded(g, getX() + inset, getY() + inset, width - inset * 2, height - inset * 2,
                selected ? TEColors.SELECTED : hover ? TEColors.HOVER : TEColors.FIELD);
            renderLabel(g, TEColors.TEXT);
            if (selected && width >= 50 && height >= 22) {
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
            int size = Math.min(16, Math.min(width - 6,height - 6));
            RenderSystem.enableBlend();
            int x=getX()+(width-size)/2,y=getY()+(height-size)/2;
            g.blit(texture,x,y,size,size,0,0,16,16,16,16);
            if(symbolOn&&!symbolMask.isEmpty()){
                var rects=symbolMask.rectangles();
                for(int i=0;i<rects.size();i++){
                    var rect=rects.get(i);
                    g.fill(x+rect.x()*size/16,y+rect.y()*size/16,x+(rect.x()+rect.width())*size/16,y+(rect.y()+rect.height())*size/16,symbolTint);
                }
            }
            RenderSystem.disableBlend();
        }
    }
}
