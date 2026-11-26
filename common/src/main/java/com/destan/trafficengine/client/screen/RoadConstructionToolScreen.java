package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.client.gui.TEChrome;
import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEIntegerSlider;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.config.ModCommonConfig;
import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.packets.cts.RoadBuilderBuildRoadPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderDataPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderResetPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderUndoPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.util.ItemData;
import com.mojang.math.Axis;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RoadConstructionToolScreen extends TrafficEngineScreen {
    private static final int WINDOW_WIDTH = 488, WINDOW_HEIGHT = 326;
    private static final int PADDING = 12, GAP = 6;
    private static final int CONTENT_WIDTH = WINDOW_WIDTH - PADDING * 2;
    private static final int SETTINGS_WIDTH = 248;
    private static final int MATERIAL_ICON_SIZE = 12;
    private static final Component START = text("start");
    private static final Component END = text("end");
    private static final Component NO_POINT = text("no_pos_defined");
    private static final Component WIDTH = text("road_width");
    private static final Component ROUTE = text("route");
    private static final Component MATERIAL = text("road_blocks");
    private static final Component RESOURCES = text("resources");
    private static final Component SELECT_POINTS = text("select_points");
    private static final Component NO_CHANGES = text("no_changes");
    private static final Component CREATIVE = text("creative_resources");
    private static final Component SELECT_A = text("select_start_world");
    private static final Component SELECT_B = text("select_end_world");
    private static final String POINT_A = "A", POINT_B = "B";

    private final ItemStack stack;
    private final RoadConstructionTool tool;
    private final WorldLocation start, end;
    private final Map<RoadType, TEButton> materials = new EnumMap<>(RoadType.class);
    private byte roadWidth;
    private boolean replaceExisting, dirtyPlan, closeHandled;
    private RoadType roadType;
    private double uiScale;
    private int forwardX, forwardY, routeSpan;
    private float routeAngle;
    private Component routeSummary = SELECT_POINTS;
    private boolean validRoute;
    private int blocksNeeded, slopesNeeded;
    private ItemStack blockIcon, slopeIcon;
    private int inventoryRevision = -1;
    private Component costStatus = SELECT_POINTS, blocksCost = Component.empty(), slopesCost = Component.empty();
    private int blockColor, slopeColor;
    private TEButton build, narrower, wider;
    private TEIntegerSlider widthSlider;

    public RoadConstructionToolScreen(ItemStack stack) {
        super(text("title"));
        this.stack = stack;
        tool = (RoadConstructionTool)stack.getItem();
        var tag = ItemData.getOrCreate(stack);
        start = tag.contains(RoadConstructionTool.NBT_LOCATION1)
            ? WorldLocation.loadFromNbt(tag.getCompound(RoadConstructionTool.NBT_LOCATION1)) : null;
        end = tag.contains(RoadConstructionTool.NBT_LOCATION2)
            ? WorldLocation.loadFromNbt(tag.getCompound(RoadConstructionTool.NBT_LOCATION2)) : null;
        roadWidth = tag.getByte(RoadConstructionTool.NBT_ROAD_WIDTH);
        replaceExisting = tag.getBoolean(RoadConstructionTool.NBT_REPLACE_BLOCKS);
        roadType = RoadType.getRoadTypeByIndex(tag.getInt(RoadConstructionTool.NBT_ROAD_TYPE));
        if (roadType == RoadType.NONE) roadType = RoadConstructionTool.DEFAULT_ROAD_TYPE;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.trafficengine.road_builder." + key, arguments);
    }

    private Component replaceLabel() {
        return text("replace_blocks").copy().append(" · ").append(replaceExisting
            ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override protected void init() {
        uiScale = Math.min(1.0, Math.min(width / (WINDOW_WIDTH + 12.0), height / (WINDOW_HEIGHT + 12.0)));
        windowWidth = WINDOW_WIDTH;
        windowHeight = WINDOW_HEIGHT;
        left = ((int)(width / uiScale) - WINDOW_WIDTH) / 2;
        top = ((int)(height / uiScale) - WINDOW_HEIGHT) / 2;
        int x = left + PADDING;
        int half = (CONTENT_WIDTH - GAP) / 2;
        addEndpoint(x, half, START, start, RoadBuilderResetPacket.START);
        addEndpoint(x + half + GAP, half, END, end, RoadBuilderResetPacket.END);

        int maxWidth = ModCommonConfig.ROAD_BUILDER_MAX_ROAD_WIDTH.get();
        roadWidth = (byte)Math.max(1, Math.min(roadWidth, maxWidth));
        int sliderY = top + 111;
        narrower = addRenderableWidget(new TEButton(x + 8, sliderY, 24, 20, Component.literal("−"),
            button -> widthSlider.setIntValue(roadWidth - 1)));
        narrower.setTooltip(Tooltip.create(text("narrower")));
        wider = addRenderableWidget(new TEButton(x + SETTINGS_WIDTH - 32, sliderY, 24, 20, Component.literal("+"),
            button -> widthSlider.setIntValue(roadWidth + 1)));
        wider.setTooltip(Tooltip.create(text("wider")));
        widthSlider = addRenderableWidget(new TEIntegerSlider(x + 36, sliderY, SETTINGS_WIDTH - 72,
            1, maxWidth, 1, roadWidth, value -> text("width_value", value), value -> {
                if (roadWidth == value) return;
                roadWidth = (byte)value;
                dirtyPlan = true;
                updateWidthButtons();
            }));
        updateWidthButtons();
        addRenderableWidget(new TEButton(x + 8, top + 136, SETTINGS_WIDTH - 16, 20, replaceLabel(), button -> {
            replaceExisting = !replaceExisting;
            button.setMessage(replaceLabel());
            dirtyPlan = true;
        })).setTooltip(Tooltip.create(text("tooltip.replace_blocks")));

        materials.clear();
        int tile = (CONTENT_WIDTH - GAP * 3) / 4 - 6;
        for (RoadType type : RoadType.values()) {
            if (type == RoadType.NONE) continue;
            int index = materials.size();
            int row = index / 4;
            int rowWidth = (4 - row) * (tile + GAP) - GAP;
            int px = x + (CONTENT_WIDTH - rowWidth) / 2 + (index % 4) * (tile + GAP);
            ItemStack icon = new ItemStack(type.getBlock());
            var button = new TEButton(px, top + 185 + row * 32, tile, 29, icon.getHoverName(), pressed -> {
                if (roadType == type) return;
                materials.get(roadType).setSelected(false);
                roadType = type;
                materials.get(type).setSelected(true);
                dirtyPlan = true;
            }) {
                @Override protected void renderLabel(GuiGraphics g, int color) {
                    renderMaterialIcon(g, icon, getX() + (width - MATERIAL_ICON_SIZE) / 2, getY() + 2);
                    center(g, getMessage(), getX(), getY() + 15, width, 13, color);
                }
            };
            button.setTooltip(Tooltip.create(icon.getHoverName()));
            materials.put(type, addRenderableWidget(button.selected(type == roadType)));
        }

        int footerY = top + WINDOW_HEIGHT - 30;
        int buttonWidth = (CONTENT_WIDTH - GAP * 3) / 4;
        addRenderableWidget(new TEButton(x, footerY, buttonWidth, 20, text("reset"), button -> {
            ModNetworkManager.RESET_ROAD_BUILDER.send(NetworkDirection.toServer(), new RoadBuilderResetPacket());
            closeWithoutSaving();
        })).setTooltip(Tooltip.create(text("tooltip.reset")));
        addRenderableWidget(new TEButton(x + buttonWidth + GAP, footerY, buttonWidth, 20, text("undo"), button -> {
            ModNetworkManager.ROAD_BUILDER_UNDO.send(NetworkDirection.toServer(), new RoadBuilderUndoPacket());
            onClose();
        })).setTooltip(Tooltip.create(text("tooltip.undo")));
        addRenderableWidget(new TEButton(x + 2 * (buttonWidth + GAP), footerY, buttonWidth, 20,
            CommonComponents.GUI_DONE, button -> onClose()));
        build = addRenderableWidget(new TEButton(x + CONTENT_WIDTH - buttonWidth, footerY, buttonWidth, 20,
            text("build"), button -> buildRoad(), true));
        addCloseButton();

        updateRoutePreview();
        updatePlan();
    }

    private void addEndpoint(int x, int w, Component heading, WorldLocation point, int endpoint) {
        Component position = point == null ? NO_POINT : Component.literal(
            String.format(Locale.ROOT, "%.0f, %.2f, %.0f", point.x, point.y, point.z));
        var button = new TEButton(x, top + 45, w, 38, heading, pressed -> {
            updateStackData();
            ModNetworkManager.RESET_ROAD_BUILDER.send(NetworkDirection.toServer(), new RoadBuilderResetPacket(endpoint));
            minecraft.player.displayClientMessage(endpoint == RoadBuilderResetPacket.START ? SELECT_A : SELECT_B, true);
            closeWithoutSaving();
        }) {
            @Override protected void renderLabel(GuiGraphics g, int color) {
                center(g, getMessage(), getX(), getY() + 4, width, 13, TEColors.ACCENT);
                center(g, position, getX(), getY() + 18, width, 15, point == null ? TEColors.MUTED : color);
            }
        };
        addRenderableWidget(button).setTooltip(Tooltip.create(text("tooltip.reselect", heading)));
    }

    private void updateWidthButtons() {
        narrower.active = roadWidth > 1;
        wider.active = roadWidth < ModCommonConfig.ROAD_BUILDER_MAX_ROAD_WIDTH.get();
    }

    private void updateRoutePreview() {
        validRoute = false;
        routeSummary = SELECT_POINTS;
        if (start == null || end == null || minecraft.level == null) return;
        var dimension = minecraft.level.dimension().location();
        if (!dimension.equals(start.dimension) || !dimension.equals(end.dimension)) {
            routeSummary = text("different_dimension");
            return;
        }
        var status = RoadConstructionTool.isLineValid(start.getLocationVec3(), end.getLocationVec3());
        if (status.flag() != PacketStatus.FLAG_OK) {
            routeSummary = RoadConstructionTool.selectionError(status);
            return;
        }
        double dx = end.x - start.x, dz = end.z - start.z;
        double factor = 1 / Math.max(Math.abs(dx) / 69, Math.abs(dz) / 9);
        forwardX = (int)Math.round(dx * factor);
        forwardY = (int)Math.round(dz * factor);
        routeAngle = (float)Math.atan2(dz, dx);
        routeSpan = (int)Math.round(Math.hypot(forwardX, forwardY));
        Locale language = Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-'));
        NumberFormat units = NumberFormat.getNumberInstance(language);
        units.setMaximumFractionDigits(3);
        double rise = end.y - start.y;
        routeSummary = text("route_details", units.format(Math.hypot(dx, dz)),
            (rise > 0 ? "+" : "") + units.format(rise));
        validRoute = true;
    }

    private void updatePlan() {
        dirtyPlan = false;
        blocksNeeded = slopesNeeded = 0;
        if (validRoute) {
            var count = tool.countBlocksNeeded(minecraft.level, start.getLocationVec3(), end.getLocationVec3(),
                roadWidth, replaceExisting, roadType, minecraft.player.isCreative());
            blocksNeeded = count.blocksCount;
            slopesNeeded = count.slopesCount;
        }
        blockIcon = new ItemStack(roadType.getBlock());
        slopeIcon = new ItemStack(roadType.getSlope());
        build.active = validRoute && blocksNeeded + slopesNeeded > 0;
        build.setTooltip(Tooltip.create(validRoute
            ? build.active ? text("tooltip.build") : NO_CHANGES : routeSummary));
        updateInventory();
    }

    private void updateInventory() {
        var inventory = minecraft.player.getInventory();
        inventoryRevision = inventory.getTimesChanged();
        boolean creative = minecraft.player.isCreative();
        if (!validRoute || blocksNeeded + slopesNeeded == 0) {
            costStatus = validRoute ? NO_CHANGES : routeSummary;
            return;
        }
        int blocks = creative ? blocksNeeded : inventory.countItem(blockIcon.getItem());
        int slopes = creative ? slopesNeeded : inventory.countItem(slopeIcon.getItem());
        blockColor = blocks >= blocksNeeded ? TEColors.TEXT : TEColors.DANGER;
        slopeColor = slopes >= slopesNeeded ? TEColors.TEXT : TEColors.DANGER;
        blocksCost = creative ? Component.literal(Integer.toString(blocksNeeded)) : text("stock", blocks, blocksNeeded);
        slopesCost = creative ? Component.literal(Integer.toString(slopesNeeded)) : text("stock", slopes, slopesNeeded);
        costStatus = creative ? CREATIVE : text("inventory_resources");
    }

    @Override public void tick() {
        if (dirtyPlan) updatePlan();
        else if (inventoryRevision != minecraft.player.getInventory().getTimesChanged()) updateInventory();
    }

    private void updateStackData() {
        var tag = ItemData.getOrCreate(stack);
        tag.putByte(RoadConstructionTool.NBT_ROAD_WIDTH, roadWidth);
        tag.putBoolean(RoadConstructionTool.NBT_REPLACE_BLOCKS, replaceExisting);
        tag.putInt(RoadConstructionTool.NBT_ROAD_TYPE, roadType.getIndex());
        ItemData.set(stack, tag);
        ModNetworkManager.UPDATE_ROAD_BUILDER.send(NetworkDirection.toServer(),
            new RoadBuilderDataPacket(replaceExisting, roadWidth, roadType));
    }

    private void buildRoad() {
        if (dirtyPlan) updatePlan();
        if (!build.active) return;
        updateStackData();
        ModNetworkManager.ROAD_BUILDER_BUILD_ROAD.send(NetworkDirection.toServer(),
            new RoadBuilderBuildRoadPacket(start, end, roadWidth, replaceExisting, roadType));
        closeWithoutSaving();
    }

    private void closeWithoutSaving() {
        closeHandled = true;
        onClose();
    }

    @Override public void onClose() {
        if (!closeHandled) {
            closeHandled = true;
            updateStackData();
        }
        super.onClose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / uiScale, y / uiScale, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }

    @Override public void mouseMoved(double x, double y) {
        super.mouseMoved(x / uiScale, y / uiScale);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, TEColors.OVERLAY);
        g.pose().pushPose();
        g.pose().scale((float)uiScale, (float)uiScale, 1);
        TEChrome.draw(g, font, left, top, WINDOW_WIDTH, WINDOW_HEIGHT, title);
        int x = left + PADDING;
        TEPanel.draw(g, x, top + 91, SETTINGS_WIDTH, 72);
        center(g, WIDTH, x, top + 95, SETTINGS_WIDTH, 14, TEColors.MUTED);
        renderRoute(g, x + SETTINGS_WIDTH + GAP, CONTENT_WIDTH - SETTINGS_WIDTH - GAP);
        center(g, MATERIAL, x, top + 167, CONTENT_WIDTH, 16, TEColors.MUTED);
        renderResources(g, x);
        super.render(g, (int)(mouseX / uiScale), (int)(mouseY / uiScale), partialTick);
        g.pose().popPose();
    }

    private void renderRoute(GuiGraphics g, int x, int w) {
        TEPanel.draw(g, x, top + 91, w, 72);
        center(g, ROUTE, x, top + 95, w, 14, TEColors.MUTED);
        if (validRoute) {
            int cx = x + w / 2, cy = top + 125;
            int thickness = Math.min(19, 4 + roadWidth);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotation(routeAngle));
            g.fill(-routeSpan, -thickness / 2, routeSpan, (thickness + 1) / 2, TEColors.ACCENT_SOFT);
            g.fill(-routeSpan, 0, routeSpan, 1, TEColors.ACCENT);
            pose.popPose();
            routePoint(g, POINT_A, cx - forwardX, cy - forwardY);
            routePoint(g, POINT_B, cx + forwardX, cy + forwardY);
        }
        center(g, routeSummary, x + 4, top + (validRoute ? 141 : 111), w - 8, validRoute ? 20 : 46, TEColors.TEXT);
    }

    private void routePoint(GuiGraphics g, String label, int x, int y) {
        TEPanel.fillRounded(g, x - 6, y - 5, 12, 11, TEColors.ACCENT);
        TEText.drawCenteredString(g, font, label, x, TEText.centeredY(g, font, label, y - 5, 11), TEColors.INK);
    }

    private void renderResources(GuiGraphics g, int x) {
        int y = top + 253;
        TEPanel.draw(g, x, y, CONTENT_WIDTH, 35);
        if (!build.active) {
            center(g, costStatus, x, y, CONTENT_WIDTH, 35, TEColors.MUTED);
            return;
        }
        int segment = CONTENT_WIDTH / 3;
        center(g, RESOURCES, x, y + 3, segment, 14, TEColors.TEXT);
        center(g, costStatus, x, y + 17, segment, 14, TEColors.MUTED);
        renderCost(g, x + segment, y, segment, blockIcon, blocksCost, blockColor);
        renderCost(g, x + segment * 2, y, segment, slopeIcon, slopesCost, slopeColor);
    }

    private void renderCost(GuiGraphics g, int x, int y, int w, ItemStack icon, Component caption, int color) {
        int gap = 5;
        int labelWidth = Math.min(w - MATERIAL_ICON_SIZE - gap - 8,
            (int)Math.ceil(TEText.width(font, caption) * TEText.layoutScale(g)) + 2);
        int iconX = x + (w - MATERIAL_ICON_SIZE - gap - labelWidth) / 2;
        renderMaterialIcon(g, icon, iconX, y + (35 - MATERIAL_ICON_SIZE) / 2);
        TEText.drawWrappedCentered(g, font, caption, iconX + MATERIAL_ICON_SIZE + gap, y, labelWidth, 35, color);
    }

    private static void renderMaterialIcon(GuiGraphics g, ItemStack icon, int x, int y) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(MATERIAL_ICON_SIZE / 16f, MATERIAL_ICON_SIZE / 16f, 1);
        g.renderItem(icon, 0, 0);
        pose.popPose();
    }

    private void center(GuiGraphics g, Component label, int x, int y, int w, int h, int color) {
        TEText.drawWrappedCentered(g, font, label, x + 4, y, w - 8, h, color);
    }
}
