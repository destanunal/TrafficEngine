package com.destan.trafficengine.client.screen;

import java.util.*;
import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.block.entity.*;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.*;
import com.destan.trafficengine.network.packets.cts.TrafficLightSchedulePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;

/** Only visible rows have controls, even for long schedules. */
public class TrafficLightScheduleEditor extends TrafficEngineScreen {
    private final Screen parent;
    private final BlockPos pos;
    private final boolean controller;
    private final TrafficLightSchedule schedule;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private static final int ROW_HEIGHT = 27;
    private static final int GREEN = 0xFF8DEA9D, RED = 0xFFFF9995;
    private int offset, rowTop, visibleRows;
    private boolean draggingScrollbar;
    private String error;
    private ItemStack triggerIcon;
    private MultiLineLabel helpLabel = MultiLineLabel.EMPTY;
    private String helpKey;
    public TrafficLightScheduleEditor(Screen parent, Level level, BlockPos pos) {
        super(Component.translatable(level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity ? "gui.trafficengine.trafficlightschedule.title_controller" : "gui.trafficengine.trafficlightschedule.title"));
        this.parent = parent;
        this.pos = pos;
        controller = level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity;
        schedule = (level.getBlockEntity(pos) instanceof TrafficLightControllerBlockEntity c ? c.getScheduleForEditing() : level.getBlockEntity(pos) instanceof TrafficLightBlockEntity l ? l.getSchedule() : new TrafficLightSchedule()).copy();
        schedule.convertToPhaseTimings(!controller);
        if (controller) schedule.convertToSequentialGreens();
    }
    private static Component text(String key) { return Component.translatable("gui.trafficengine.trafficlightschedule." + key); }
    @Override protected void init() {
        rows.clear();
        helpKey = null;
        triggerIcon = schedule.getTrigger().getIconStack();
        layoutWindow(400, controller ? 355 : 290);
        addCloseButton();
        int inner = windowWidth - 24, half = (inner - 4) / 2;
        addRenderableWidget(new TEButton(left + 12, top + 44, half, 20, schedule.getTrigger().getValueTranslation(), b -> {
            schedule.setTrigger(TrafficLightTrigger.values()[(schedule.getTrigger().ordinal() + 1) % TrafficLightTrigger.values().length]);
            triggerIcon = schedule.getTrigger().getIconStack();
            b.setMessage(schedule.getTrigger().getValueTranslation());
        }) {
            @Override protected void renderLabel(GuiGraphics g, int color) {
                g.renderItem(triggerIcon, getX() + 3, getY() + 2);
                g.drawString(font, getMessage(), getX() + 22 + Math.max(0, (getWidth() - 26 - font.width(getMessage())) / 2), getY() + 6, color, false);
            }
        });
        Component blinkLabel = text("blink_toggle").copy().append(": ");
        TEButton blink = addRenderableWidget(new TEButton(left + 16 + half, top + 44, inner - half - 4, 20, blinkCaption(), b -> {
            schedule.setBlinkAtEnd(!schedule.isBlinkAtEnd());
            b.setMessage(blinkCaption());
        }) {
            @Override protected void renderLabel(GuiGraphics g, int color) {
                Component state = schedule.isBlinkAtEnd() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
                int x = getX() + (getWidth() - font.width(blinkLabel) - font.width(state)) / 2;
                int y = getY() + (getHeight() - font.lineHeight) / 2;
                g.drawString(font, blinkLabel, x, y, color, false);
                g.drawString(font, state, x + font.width(blinkLabel), y + 1,
                    schedule.isBlinkAtEnd() ? 0xFF55FF55 : 0xFFFF5555, false);
            }
        });
        blink.setTooltip(Tooltip.create(text("blink_help")));
        if (controller) {
            addRenderableWidget(new TEButton(left + 12, top + 68, inner, 20, modeCaption(), b -> {
                if (!validFields()) return;
                schedule.setManualController(!schedule.isManualController());
                error = null;
                b.setMessage(modeCaption());
                rebuildRows();
            }));
            addRenderableWidget(new TEButton(left + 103, top + 93, 100, 20, transitionCaption(), b -> {
                schedule.setRedYellowBeforeGreen(!schedule.isRedYellowBeforeGreen());
                b.setMessage(transitionCaption());
            })).setTooltip(Tooltip.create(text("red_yellow_help")));
            addRenderableWidget(new TEScheduleNumberBox(font, left + windowWidth - 64, top + 94, 50, schedule.getRedYellowSeconds(), 0, TrafficLightSchedule.MAX_RED_YELLOW_SECONDS, true, true, text("red_yellow_seconds"), value -> schedule.setRedYellowSeconds((int)value)));
        }
        rowTop = top + (controller ? 170 : 113);
        visibleRows = Math.max(1, (top + windowHeight - 41 - rowTop) / ROW_HEIGHT);
        addRenderableWidget(new TEButton(left + 12, top + windowHeight - 29, 138, 20, text(controller ? "add_direction" : "add_entry"), b -> {
            if (!validFields()) return;
            TrafficLightScheduleEntryData e = new TrafficLightScheduleEntryData();
            if (controller) {
                int id = TrafficLightSchedule.MIN_PHASE_ID;
                Set<Integer> used = new HashSet<>();
                for (var entry : schedule.getEntries()) used.add(entry.getPhaseId());
                while (used.contains(id) && id <= TrafficLightSchedule.MAX_PHASE_ID) id++;
                if (id > TrafficLightSchedule.MAX_PHASE_ID) { error = "invalid_id"; return; }
                e.setPhaseId(id);
                e.enableOnlyColors(List.of(TrafficLightColor.GREEN));
            }
            schedule.getEntries().add(e);
            offset = Math.max(0, schedule.getEntries().size() - visibleRows);
            rebuildRows();
        }));
        addRenderableWidget(new TEButton(left + windowWidth - 184, top + windowHeight - 29, 82, 20, CommonComponents.GUI_CANCEL, b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 94, top + windowHeight - 29, 82, 20, CommonComponents.GUI_DONE, b -> save()).primary());
        rebuildRows();
    }
    private Component blinkCaption() {
        return text("blink_toggle").copy().append(": ").append(
            (schedule.isBlinkAtEnd() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).copy()
                .withStyle(schedule.isBlinkAtEnd() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
    private Component modeCaption() {
        return text("controller_mode").copy().append(": ").append(text(schedule.isManualController() ? "mode_manual" : "mode_automatic").copy()
            .withStyle(schedule.isManualController() ? ChatFormatting.AQUA : ChatFormatting.GREEN));
    }
    private static Component colorText(String key, ChatFormatting color) { return text("color." + key).copy().withStyle(color); }
    private Component transitionCaption() {
        return schedule.isRedYellowBeforeGreen()
            ? colorText("red", ChatFormatting.RED).copy().append(Component.literal(" + ").withStyle(ChatFormatting.WHITE)).append(colorText("yellow", ChatFormatting.YELLOW))
            : colorText("yellow", ChatFormatting.YELLOW);
    }
    private <T extends AbstractWidget> T row(T widget) { rows.add(widget); return addRenderableWidget(widget); }
    private void rebuildRows() {
        rows.forEach(this::removeWidget);
        rows.clear();
        offset = Math.max(0, Math.min(offset, schedule.getEntries().size() - visibleRows));
        for (int i = offset; i < Math.min(schedule.getEntries().size(), offset + visibleRows); i++) {
            final int index = i;
            TrafficLightScheduleEntryData e = schedule.getEntries().get(i);
            int y = rowTop + (i - offset) * ROW_HEIGHT;
            if (controller) {
                row(new TEScheduleNumberBox(font, left + 20, y + 4, 62, e.getPhaseId(), TrafficLightSchedule.MIN_PHASE_ID, TrafficLightSchedule.MAX_PHASE_ID, true, false, text("phase_id"), value -> e.setPhaseId((int)value)));
                row(new TEScheduleNumberBox(font, left + 102, y + 4, 65, e.getDurationSeconds(), 0, TrafficLightScheduleEntryData.MAX_SECONDS, false, true, text("green_seconds"), e::setDurationSeconds)).setTextColor(GREEN);
                if (schedule.isManualController()) row(new TEScheduleNumberBox(font, left + 190, y + 4, 65, e.getManualRedSeconds(), 0, TrafficLightScheduleEntryData.MAX_SECONDS, false, true, text("red_seconds"), e::setManualRedSeconds)).setTextColor(RED);
            } else {
                row(new TEButton(left + 24, y + 4, 172, 19, colorCaption(e), b -> {
                    int choice = (colorIndex(e) + 1) % 5;
                    e.enableOnlyColors(switch (choice) { case 1 -> List.of(TrafficLightColor.RED); case 2 -> List.of(TrafficLightColor.YELLOW); case 3 -> List.of(TrafficLightColor.GREEN); case 4 -> List.of(TrafficLightColor.RED, TrafficLightColor.YELLOW); default -> List.of(); });
                    b.setMessage(colorCaption(e));
                }));
                row(new TEScheduleNumberBox(font, left + 215, y + 4, 64, e.getDurationSeconds(), 0, TrafficLightScheduleEntryData.MAX_SECONDS, false, true, text("seconds"), e::setDurationSeconds));
            }
            for (int k = 0; k < 3; k++) {
                final int action = k;
                TEButton button = row(new TEButton(left + windowWidth - 88 + k * 22, y + 4, 20, 19, Component.literal(k == 0 ? "↑" : k == 1 ? "↓" : "×"), b -> {
                    if (!validFields()) return;
                    if (action == 2) schedule.getEntries().remove(index);
                    else if (action == 0 && index > 0) Collections.swap(schedule.getEntries(), index, index - 1);
                    else if (action == 1 && index + 1 < schedule.getEntries().size()) Collections.swap(schedule.getEntries(), index, index + 1);
                    error = null; rebuildRows();
                }) {
                    @Override protected void renderLabel(GuiGraphics g, int color) {
                        TEWorkbenchIcons.draw(g, action == 0 ? ModGuiIcons.MOVE_UP : action == 1 ? ModGuiIcons.MOVE_DOWN : ModGuiIcons.DELETE,
                            getX() + 2, getY() + 1, active ? TEColors.TEXT : TEColors.MUTED);
                    }
                });
                button.setTooltip(Tooltip.create(text(k == 0 ? "move_up" : k == 1 ? "move_down" : "delete")));
                button.active = k == 2 || (k == 0 ? i > 0 : i + 1 < schedule.getEntries().size());
            }
        }
    }
    private int colorIndex(TrafficLightScheduleEntryData e) {
        boolean r = e.getEnabledColors().stream().anyMatch(c -> c.isSimilar(TrafficLightColor.RED));
        boolean y = e.getEnabledColors().stream().anyMatch(c -> c.isSimilar(TrafficLightColor.YELLOW));
        boolean g = e.getEnabledColors().stream().anyMatch(c -> c.isSimilar(TrafficLightColor.GREEN));
        return r && y ? 4 : r ? 1 : y ? 2 : g ? 3 : 0;
    }
    private Component colorCaption(TrafficLightScheduleEntryData e) {
        return switch (colorIndex(e)) { case 1 -> colorText("red", ChatFormatting.RED); case 2 -> colorText("yellow", ChatFormatting.YELLOW); case 3 -> colorText("green", ChatFormatting.GREEN); case 4 -> colorText("red", ChatFormatting.RED).copy().append(Component.literal(" + ").withStyle(ChatFormatting.WHITE)).append(colorText("yellow", ChatFormatting.YELLOW)); default -> text("color.off"); };
    }
    private boolean validFields() {
        for (var c : children()) if (c instanceof TEScheduleNumberBox n && !n.isValid()) { error = controller && n.isInteger() && rows.contains(c) ? "invalid_id" : "invalid_seconds"; return false; }
        return true;
    }
    private void save() {
        if (!validFields()) return;
        error = null;
        if (controller) {
            Set<Integer> ids = new HashSet<>();
            int common = -1, minimum = 0;
            for (TrafficLightScheduleEntryData e : schedule.getEntries()) {
                if (!TrafficLightSchedule.isValidPhaseId(e.getPhaseId())) { error = "invalid_id"; break; }
                if (!ids.add(e.getPhaseId())) { error = "duplicate_id"; break; }
                if (e.getDurationTicks() <= 0) { error = "invalid_seconds"; break; }
                if (schedule.isManualController()) {
                    if (e.getManualRedTicks() <= 0) { error = "invalid_manual_red"; break; }
                    int cycle = e.getDurationTicks() + e.getManualRedTicks() + schedule.getRedYellowTicks() * 2;
                    if (common >= 0 && common != cycle) { error = "invalid_manual_cycle"; break; }
                    common = cycle;
                    minimum += e.getDurationTicks() + TrafficLightSchedule.SAFETY_INTERVAL_TICKS + schedule.getRedYellowTicks() * 2;
                }
            }
            if (error == null && schedule.isManualController() && common >= 0 && common < minimum) error = "invalid_manual_overlap";
        }
        if (error != null) return;
        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_SCHEDULE.send(NetworkDirection.toServer(), new TrafficLightSchedulePacket(pos, List.of(schedule)));
        onClose();
    }
    private int maxOffset() { return Math.max(0, schedule.getEntries().size() - visibleRows); }
    private boolean insideRows(double x, double y) { return x >= left + 12 && x < left + windowWidth - 12 && y >= rowTop && y < rowTop + visibleRows * ROW_HEIGHT; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (maxOffset() > 0 && insideRows(x, y) && validFields()) { offset += delta > 0 ? -1 : 1; rebuildRows(); return true; }
        return super.mouseScrolled(x, y, horizontal, delta);
    }
    private void scrollTo(double y) {
        int height = visibleRows * ROW_HEIGHT;
        int thumb = Math.max(18, height * visibleRows / schedule.getEntries().size());
        offset = Math.max(0, Math.min(maxOffset(), (int)Math.round((y - rowTop - thumb / 2D) / Math.max(1, height - thumb) * maxOffset())));
        rebuildRows();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && maxOffset() > 0 && insideRows(x, y) && x >= left + windowWidth - 20 && validFields()) {
            draggingScrollbar = true; scrollTo(y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar) { scrollTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        draggingScrollbar = false; return super.mouseReleased(x, y, button);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderWindow(g);
        if (controller) {
            g.drawString(font, text("transition_light"), left + 12, top + 98, TEColors.MUTED, false);
            g.drawString(font, text("red_yellow_seconds"), left + 215, top + 98, TEColors.MUTED, false);
        }
        String key = error != null ? error : controller ? schedule.isManualController() ? "help_manual" : "help_sequence" : "help_own";
        if (!key.equals(helpKey)) { helpLabel = MultiLineLabel.create(font, windowWidth - 24, 2, text(key)); helpKey = key; }
        helpLabel.renderLeftAlignedNoShadow(g, left + 12, top + (controller ? 117 : 68), 10, error == null ? TEColors.MUTED : 0xFFFF8888);
        if (controller) g.drawString(font, font.plainSubstrByWidth(text("help_pedestrian").getString(), windowWidth - 24), left + 12, top + 139, TEColors.MUTED, false);
        int headerY = rowTop - 14;
        TEPanel.draw(g, left + 12, rowTop - 18, windowWidth - 24, visibleRows * ROW_HEIGHT + 22);
        g.fill(left + 13, rowTop - 1, left + windowWidth - 13, rowTop, TEColors.BORDER);
        if (controller) {
            g.drawString(font, text("phase_id"), left + 20, headerY, TEColors.MUTED, false);
            Component greenLabel = text("green_seconds"), redLabel = text("red_seconds");
            g.drawString(font, greenLabel, left + 102 + (65 - font.width(greenLabel)) / 2, headerY, GREEN, false);
            g.drawString(font, redLabel, left + 190 + (65 - font.width(redLabel)) / 2, headerY, RED, false);
        } else {
            g.drawString(font, text("color"), left + 24, headerY, TEColors.MUTED, false);
            g.drawString(font, text("seconds"), left + 215, headerY, TEColors.MUTED, false);
        }
        int cycleTicks = controller && !schedule.isManualController() ? schedule.getSequentialCycleTicks() : 0;
        for (int i = offset; i < Math.min(schedule.getEntries().size(), offset + visibleRows); i++) {
            int y = rowTop + (i - offset) * ROW_HEIGHT;
            if ((i - offset) % 2 == 1) g.fill(left + 13, y, left + windowWidth - 13, y + ROW_HEIGHT, TEColors.WINDOW);
            if (controller && !schedule.isManualController()) {
                var e = schedule.getEntries().get(i);
                int red = e.getDurationTicks() <= 0 ? 0 : Math.max(0, cycleTicks - e.getDurationTicks() - schedule.getRedYellowTicks() * 2);
                String seconds = Integer.toString(red / 20);
                g.drawString(font, seconds, left + 190 + (65 - font.width(seconds)) / 2, y + 9, RED, false);
            }
        }
        if (maxOffset() > 0) {
            int height = visibleRows * ROW_HEIGHT;
            int thumb = Math.max(18, height * visibleRows / schedule.getEntries().size());
            int sy = rowTop + (height - thumb) * offset / maxOffset();
            g.fill(left + windowWidth - 18, rowTop, left + windowWidth - 14, rowTop + height, TEColors.FIELD);
            g.fill(left + windowWidth - 18, sy, left + windowWidth - 14, sy + thumb, TEColors.ACCENT);
        }
        super.render(g, mx, my, tick);
    }
}
