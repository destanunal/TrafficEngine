package com.destan.trafficengine.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import de.mrjulsen.mcdragonlib.DragonLib;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.Direction;
import de.mrjulsen.mcdragonlib.client.gui.widgets.render.VanillaSimpleButtonRenderer;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.RenderLayer;
import de.mrjulsen.mcdragonlib.client.render.DLTextureSheet;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.DLSprite;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.data.TrafficLightTrigger;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.block.entity.TrafficLightControllerBlockEntity;
import com.destan.trafficengine.client.widgets.data.TrafficLightScheduleEditorWidget;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.network.packets.cts.TrafficLightSchedulePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class TrafficLightScheduleEditor extends DLWindow {

    public static final ResourceLocation WIDGETS = new ResourceLocation(TrafficEngine.MOD_ID, "textures/gui/traffic_light_schedule_icons.png");
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    public static final int WINDOW_WIDTH = 360;
    public static final int WINDOW_HEIGHT = 300;
    public static final int PADDING = 7;
    public static final int TOP_PADDING = 20;
    public static final int BOTTOM_PADDING = PADDING + 23;
    public static final int SCROLLBAR_WIDTH = 8;
    public static final int ENTRY_PADDING = 8;
    public static final int DEFAULT_ENTRY_HEIGHT = 18;
    public static final int TIMELINE_UW = 9;
    public static final int TIMELINE_VH = 9;
    public static final int ENTRY_TIMELINE_COLUMN_WIDTH = 20;

    private DLPanel areaHeader;
    private TrafficLightScheduleEditorWidget container;
    private static final int TRANSITION_BUTTON_X = 72;
    private static final int TRANSITION_BUTTON_WIDTH = 116;

    private final Map<Integer, TrafficLightType> phaseIdTypes = new HashMap<>();

    // settings
    private final BlockPos pos;
    private final Level level;
    private final boolean isController;
    private final TrafficLightSchedule schedule;
    private boolean duplicateId = false;
    private boolean invalidSeconds = false;
    private boolean invalidManualRed = false;
    private boolean invalidManualCycle = false;
    private boolean invalidManualOverlap = false;

    //texts
    private final Component title = TextUtils.translate("gui.trafficengine.trafficlightschedule.title");
    private final Component textAddEntry = TextUtils.translate("gui.trafficengine.trafficlightschedule.add_entry");
    private final Component textBlink = TextUtils.translate("gui.trafficengine.trafficlightschedule.blink_toggle");

    public TrafficLightScheduleEditor(DLWindowManager manager, Level level, BlockPos pos) {
        super(manager);
        this.pos = pos;
        this.level = level;
        this.isController = isController();
        schedule = getSchedule().copy();
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT + (isController ? 85 : 0));
        windowSpawnPosition.set(WindowPosition.CENTER);

        if (isController()) {
            if (level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity blockEntity) {
                blockEntity.getTrafficLightLocations().stream().filter(x -> 
                    level.isLoaded(x.getLocationBlockPos()) &&
                    level.getBlockEntity(x.getLocationBlockPos()) instanceof TrafficLightBlockEntity
                ).map(x -> (TrafficLightBlockEntity)level.getBlockEntity(x.getLocationBlockPos())).forEach(x -> {
                    int phaseId = x.getPhaseId();
                    TrafficLightType type = x.getTLType();
                    if (phaseIdTypes.containsKey(phaseId)) {
                        TrafficLightType savedType = phaseIdTypes.get(phaseId);
                        if (savedType != null && savedType != type) {
                            phaseIdTypes.remove(phaseId);
                            phaseIdTypes.put(phaseId, type);
                        }
                    } else {
                        phaseIdTypes.put(phaseId, type);
                    }
                });
            }
        } else {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TrafficLightBlockEntity blockEntity) {
                phaseIdTypes.put(0, blockEntity.getTLType());
            }
        }

        schedule.convertToPhaseTimings(!isController);
        if (isController) schedule.convertToSequentialGreens();

        areaHeader = addComponent(new DLPanel(PADDING, TOP_PADDING, width() - PADDING * 2, 22));
        FlowLayout headerLayout = new FlowLayout();
        headerLayout.padding.set(new Padding(1));
        headerLayout.wrap.set(false);
        headerLayout.flowDirection.set(Direction.HORIZONTAL);
        areaHeader.addEventListener(DLGuiStandardEvents.RenderEvent.class, (s, e) -> {
            if (e.layer() == RenderLayer.MAIN) {
                DLTextureSheet.DRAGONLIB_UI.getSprite("button_brown_down").render(e.graphics(), 0, 0, s.width(), s.height());
            }            
            return false;
        });

        DLCycleButton<TrafficLightTrigger> triggerBtn = areaHeader.addComponent(new DLCycleButton<>(0, 0, 1, 20));
        triggerBtn.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        triggerBtn.textColor.set(DragonLib.VANILLA_UI_FONT_COLOR);
        triggerBtn.drawFontShadow.set(false);
        triggerBtn.componentRenderer.set(VanillaSimpleButtonRenderer.VANILLA_BUTTON_BROWN);
        triggerBtn.textFormat.set(c -> c.selectedItem.get().map(e -> e.getValueTranslation()).orElse(TextUtils.empty()));
        triggerBtn.icon.set(new DLSprite(schedule.getTrigger().getIconStack(), 16, false));
        triggerBtn.iconAlignment.set(ETextAlignment.LEFT);
        triggerBtn.textAlignment.set(ETextAlignment.LEFT);
        triggerBtn.items.addAll(TrafficLightTrigger.values());
        triggerBtn.selectedItem.set(Optional.of(schedule.getTrigger()));
        triggerBtn.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            triggerBtn.selectedItem.get().ifPresent(c -> {
                schedule.setTrigger(c);
                triggerBtn.icon.set(new DLSprite(c.getIconStack(), 16, false));
            });
            return false;
        });

        DLCycleButton<Boolean> blinkBtn = areaHeader.addComponent(new DLCycleButton<>(0, 0, 1, 20));
        blinkBtn.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        blinkBtn.textColor.set(DragonLib.VANILLA_UI_FONT_COLOR);
        blinkBtn.drawFontShadow.set(false);
        blinkBtn.componentRenderer.set(VanillaSimpleButtonRenderer.VANILLA_BUTTON_BROWN);
        blinkBtn.text.set(textBlink);
        blinkBtn.textFormat.set(c -> TextUtils.text(c.text.get().getString()).append(": ").append(c.selectedItem.get().map(b -> b ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).orElse(CommonComponents.OPTION_OFF)));
        blinkBtn.items.addAll(true, false);
        blinkBtn.selectedItem.set(Optional.of(schedule.isBlinkAtEnd()));
        blinkBtn.tooltip.set(new DLTooltip(List.of(TextUtils.translate("gui.trafficengine.trafficlightschedule.blink_help")), 250));
        blinkBtn.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            blinkBtn.selectedItem.get().ifPresent(c -> {
                schedule.setBlinkAtEnd(c);
            });
            return false;
        });
        areaHeader.layout.set(headerLayout);

        if (isController) {
            DLCycleButton<Boolean> controllerMode = addComponent(new DLCycleButton<>(
                12, TOP_PADDING + areaHeader.height() + 4, width() - 24, 18));
            controllerMode.drawFontShadow.set(false);
            controllerMode.componentRenderer.set(VanillaSimpleButtonRenderer.VANILLA_BUTTON_GRAY);
            controllerMode.items.addAll(false, true);
            controllerMode.selectedItem.set(Optional.of(schedule.isManualController()));
            controllerMode.textFormat.set(c -> TextUtils.translate("gui.trafficengine.trafficlightschedule.controller_mode")
                .append(": ").append(TextUtils.translate(c.selectedItem.get().orElse(false)
                    ? "gui.trafficengine.trafficlightschedule.mode_manual"
                    : "gui.trafficengine.trafficlightschedule.mode_automatic")
                    .withStyle(c.selectedItem.get().orElse(false) ? ChatFormatting.AQUA : ChatFormatting.GREEN)));
            controllerMode.tooltip.set(new DLTooltip(List.of(
                TextUtils.translate("gui.trafficengine.trafficlightschedule.mode_help")), 250));
            controllerMode.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
                controllerMode.selectedItem.get().ifPresent(schedule::setManualController);
                if (container != null) container.refresh();
                return false;
            });

            DLCycleButton<Boolean> yellowMode = addComponent(new DLCycleButton<>(
                TRANSITION_BUTTON_X, TOP_PADDING + areaHeader.height() + 28, TRANSITION_BUTTON_WIDTH, 18));
            yellowMode.drawFontShadow.set(false);
            yellowMode.componentRenderer.set(VanillaSimpleButtonRenderer.VANILLA_BUTTON_GRAY);
            yellowMode.items.addAll(false, true);
            yellowMode.selectedItem.set(Optional.of(schedule.isRedYellowBeforeGreen()));
            yellowMode.textFormat.set(c -> c.selectedItem.get().orElse(true)
                ? TextUtils.translate("gui.trafficengine.trafficlightschedule.color.red").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(TextUtils.text(" + ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(TextUtils.translate("gui.trafficengine.trafficlightschedule.color.yellow").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                : TextUtils.translate("gui.trafficengine.trafficlightschedule.color.yellow").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            yellowMode.tooltip.set(new DLTooltip(List.of(
                TextUtils.translate(schedule.isRedYellowBeforeGreen()
                    ? "gui.trafficengine.trafficlightschedule.before_green_red_yellow_help"
                    : "gui.trafficengine.trafficlightschedule.before_green_yellow_help")), 300));
            yellowMode.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
                yellowMode.selectedItem.get().ifPresent(selected -> {
                    schedule.setRedYellowBeforeGreen(selected);
                    yellowMode.tooltip.set(new DLTooltip(List.of(TextUtils.translate(selected
                        ? "gui.trafficengine.trafficlightschedule.before_green_red_yellow_help"
                        : "gui.trafficengine.trafficlightschedule.before_green_yellow_help")), 300));
                });
                return false;
            });

            DLNumberPicker redYellowSeconds = addComponent(new DLNumberPicker(
                width() - PADDING - 70, TOP_PADDING + areaHeader.height() + 28, 70, 18));
            redYellowSeconds.min.set(0D);
            redYellowSeconds.max.set((double)TrafficLightSchedule.MAX_RED_YELLOW_SECONDS);
            redYellowSeconds.value.set((double)schedule.getRedYellowSeconds());
            redYellowSeconds.tooltip.set(new DLTooltip(List.of(
                TextUtils.translate("gui.trafficengine.trafficlightschedule.red_yellow_help")), 250));
            redYellowSeconds.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
                schedule.setRedYellowSeconds((int)e.value());
                return false;
            });
        }

        // add entry btn
        DLButton addBtn = addComponent(new DLButton(PADDING, height() - PADDING - 20, 116, 20));
        addBtn.text.set(isController ? TextUtils.translate("gui.trafficengine.trafficlightschedule.add_direction") : textAddEntry);
        addBtn.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            createNewEntry();
            container.refresh();
            return false;
        });
        addBtn.tooltip.set(new DLTooltip(List.of(isController
            ? TextUtils.translate("gui.trafficengine.trafficlightschedule.add_direction") : textAddEntry), 200));


        
        DLButton cancelBtn = addComponent(new DLButton(width() - PADDING - 90, height() - PADDING - 20, 90, 20));
        cancelBtn.text.set(CommonComponents.GUI_CANCEL);
        cancelBtn.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            getWindowManager().closeWindow(this);
            return false;
        });

        DLButton doneBtn = addComponent(new DLButton(width() - PADDING - 184, height() - PADDING - 20, 90, 20));
        doneBtn.text.set(CommonComponents.GUI_DONE);
        doneBtn.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            onDone();
            return false;
        });

        int contentOffset = isController ? 93 : 27;
        container = addComponent(new TrafficLightScheduleEditorWidget(PADDING, TOP_PADDING + areaHeader.height() + contentOffset,
            width() - PADDING * 2, height() - TOP_PADDING - areaHeader.height() - BOTTOM_PADDING - contentOffset,
            schedule, isController(), getPhaseTypes()));
    
    }

    private boolean isController() {
        return level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity;
    }

    private TrafficLightSchedule getSchedule() {
        if (isController && level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity blockEntity) {
            return blockEntity.getScheduleForEditing();
        } else if (level.getBlockEntity(pos) instanceof TrafficLightBlockEntity blockEntity) {
            return blockEntity.getSchedule();
        }

        return new TrafficLightSchedule();
    }

    public Map<Integer, TrafficLightType> getPhaseTypes() {
        return phaseIdTypes;
    }


    protected void onDone() {
        if (isController) {
            duplicateId = false;
            invalidSeconds = false;
            invalidManualRed = false;
            invalidManualCycle = false;
            invalidManualOverlap = false;
            Set<Integer> ids = new HashSet<>();
            int commonCycle = -1;
            int minimumCycle = 0;
            for (TrafficLightScheduleEntryData entry : schedule.getEntries()) {
                if (!ids.add(entry.getPhaseId())) {
                    duplicateId = true;
                    return;
                }
                if (entry.getDurationTicks() <= 0) {
                    invalidSeconds = true;
                    return;
                }
                if (schedule.isManualController()) {
                    if (entry.getManualRedTicks() <= 0) {
                        invalidManualRed = true;
                        return;
                    }
                    int entryCycle = entry.getDurationTicks() + entry.getManualRedTicks()
                        + schedule.getRedYellowTicks() * 2;
                    if (commonCycle >= 0 && commonCycle != entryCycle) {
                        invalidManualCycle = true;
                        return;
                    }
                    commonCycle = entryCycle;
                    minimumCycle += entry.getDurationTicks() + TrafficLightSchedule.SAFETY_INTERVAL_TICKS
                        + schedule.getRedYellowTicks() * 2;
                }
            }
            if (schedule.isManualController() && commonCycle >= 0 && commonCycle < minimumCycle) {
                invalidManualOverlap = true;
                return;
            }
        }
        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_SCHEDULE.send(NetworkDirection.toServer(), new TrafficLightSchedulePacket(pos, List.of(schedule)));
        getWindowManager().closeWindow(this);
    }

    private void createNewEntry() {
        TrafficLightScheduleEntryData entry = new TrafficLightScheduleEntryData();
        if (isController) {
            int nextId = schedule.getEntries().stream().mapToInt(TrafficLightScheduleEntryData::getPhaseId).max().orElse(0) + 1;
            entry.setPhaseId(nextId);
            entry.enableOnlyColors(List.of(TrafficLightColor.GREEN));
        }
        schedule.getEntries().add(entry);
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        DLTextureSheet.DRAGONLIB_UI.getSprite(DLTextureSheet.SPRITE_NAME_WINDOW_ROUNDED).render(graphics, 0, 0, width(), height());
        GuiUtils.drawString(graphics, graphics.defaultFont(), width() / 2, 7,
            isController ? TextUtils.translate("gui.trafficengine.trafficlightschedule.title_controller") : title,
            DragonLib.VANILLA_UI_FONT_COLOR, ETextAlignment.CENTER, false);
        if (isController) {
            int transitionButtonY = TOP_PADDING + areaHeader.height() + 28;
            GuiUtils.fill(graphics, TRANSITION_BUTTON_X - 2, transitionButtonY - 2,
                TRANSITION_BUTTON_WIDTH + 4, 22, DLColor.fromInt(0xFF414141));
            GuiUtils.fill(graphics, TRANSITION_BUTTON_X - 1, transitionButtonY - 1,
                TRANSITION_BUTTON_WIDTH + 2, 1, DLColor.fromInt(0xFFB8B8B8));
            MultiLineLabel.create(graphics.defaultFont(),
                TextUtils.translate("gui.trafficengine.trafficlightschedule.transition_light"),
                TRANSITION_BUTTON_X - 14, 2)
                .renderLeftAlignedNoShadow(graphics.graphics(), 10, transitionButtonY + 4,
                    graphics.defaultFont().lineHeight + 2, 0xFF404040);
            GuiUtils.drawString(graphics, graphics.defaultFont(), 205, transitionButtonY + 5,
                TextUtils.translate("gui.trafficengine.trafficlightschedule.red_yellow_seconds"),
                DragonLib.VANILLA_UI_FONT_COLOR, ETextAlignment.LEFT, false);
        }
        int helpOffset = isController ? 55 : 0;
        drawWrappedHelp(graphics, 10, TOP_PADDING + areaHeader.height() + helpOffset + 3,
            TextUtils.translate(duplicateId ? "gui.trafficengine.trafficlightschedule.duplicate_id"
                : invalidSeconds ? "gui.trafficengine.trafficlightschedule.invalid_seconds"
                : invalidManualRed ? "gui.trafficengine.trafficlightschedule.invalid_manual_red"
                : invalidManualCycle ? "gui.trafficengine.trafficlightschedule.invalid_manual_cycle"
                : invalidManualOverlap ? "gui.trafficengine.trafficlightschedule.invalid_manual_overlap"
                : isController ? schedule.isManualController()
                    ? "gui.trafficengine.trafficlightschedule.help_manual"
                    : "gui.trafficengine.trafficlightschedule.help_sequence"
                    : "gui.trafficengine.trafficlightschedule.help_own"),
            duplicateId || invalidSeconds || invalidManualRed || invalidManualCycle || invalidManualOverlap
                ? 0xFFFF5555 : 0xFF404040);
        if (isController) drawWrappedHelp(graphics, 10, TOP_PADDING + areaHeader.height() + 71,
            TextUtils.translate("gui.trafficengine.trafficlightschedule.help_pedestrian"),
            0xFF404040);
    }

    private void drawWrappedHelp(DLGuiGraphics graphics, int x, int y, Component message, int color) {
        MultiLineLabel.create(graphics.defaultFont(), message, width() - x - 4, 2)
            .renderLeftAlignedNoShadow(graphics.graphics(), x, y, graphics.defaultFont().lineHeight + 1, color);
    }
}
