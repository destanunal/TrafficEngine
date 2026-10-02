package com.destan.trafficengine.client.screen;

import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEIntegerSlider;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.config.ModCommonConfig;
import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.network.packets.cts.RoadBuilderBuildRoadPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderDataPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderResetPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RoadConstructionToolScreen extends TrafficEngineScreen {
    private final ItemStack stack;
    private final WorldLocation pos1, pos2;
    private final String position1, position2;
    private byte roadWidth;
    private boolean replaceExistingBlocks;
    private RoadType roadType;
    private int blocksCount, slopesCount;
    private ItemStack blockIcon, slopeIcon;
    private String blockCount, slopeCount;
    private TEButton build;
    private final List<TEButton> materials = new ArrayList<>();
    public RoadConstructionToolScreen(ItemStack stack, int blocks, int slopes) {
        super(text("title"));
        if (!(stack.getItem() instanceof RoadConstructionTool)) throw new IllegalArgumentException("Invalid road construction tool");
        this.stack = stack;
        var nbt = stack.getOrCreateTag();
        pos1 = nbt.contains(RoadConstructionTool.NBT_LOCATION1) ? WorldLocation.loadFromNbt(nbt.getCompound(RoadConstructionTool.NBT_LOCATION1)) : null;
        pos2 = nbt.contains(RoadConstructionTool.NBT_LOCATION2) ? WorldLocation.loadFromNbt(nbt.getCompound(RoadConstructionTool.NBT_LOCATION2)) : null;
        position1 = position(pos1); position2 = position(pos2);
        roadWidth = (byte)Math.max(1, nbt.getByte(RoadConstructionTool.NBT_ROAD_WIDTH));
        replaceExistingBlocks = nbt.getBoolean(RoadConstructionTool.NBT_REPLACE_BLOCKS);
        roadType = RoadType.getRoadTypeByIndex(nbt.getInt(RoadConstructionTool.NBT_ROAD_TYPE));
        if (roadType == RoadType.NONE) roadType = RoadType.ASPHALT;
        blocksCount = blocks; slopesCount = slopes;
        updateResources(false);
    }
    private static Component text(String key) { return Component.translatable("gui.trafficengine.road_builder." + key); }
    private static String position(WorldLocation location) {
        return location == null ? text("no_pos_defined").getString() : String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", location.x, location.y, location.z);
    }
    private Component replaceLabel() { return text("replace_blocks").copy().append(": ").append(replaceExistingBlocks ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF); }
    private void updateResources(boolean recount) {
        if (recount && pos1 != null && pos2 != null) {
            var count = RoadConstructionTool.countBlocksNeeded(minecraft.level, pos1.getLocationVec3(), pos2.getLocationVec3(), roadWidth, replaceExistingBlocks);
            blocksCount = count.blocksCount; slopesCount = count.slopesCount;
        }
        blockIcon = new ItemStack(roadType.getBlock()); slopeIcon = new ItemStack(roadType.getSlope());
        blockCount = "× " + blocksCount; slopeCount = "× " + slopesCount;
        if (build != null) build.active = pos1 != null && pos2 != null && roadWidth > 0;
    }
    @Override protected void init() {
        layoutWindow(400, 265);
        int x = left + 14, w = windowWidth - 28, half = (w - 6) / 2;
        materials.clear();
        addRenderableWidget(new TEButton(x, top + 50, half, 20, Component.literal(position1), b -> {})).setTooltip(Tooltip.create(text("tooltip.pos1")));
        addRenderableWidget(new TEButton(x + half + 6, top + 50, half, 20, Component.literal(position2), b -> {})).setTooltip(Tooltip.create(text("tooltip.pos2")));
        addRenderableWidget(new TEButton(x, top + 82, half, 20, replaceLabel(), b -> {
            replaceExistingBlocks = !replaceExistingBlocks; b.setMessage(replaceLabel()); updateResources(true);
        })).setTooltip(Tooltip.create(text("tooltip.replace_blocks")));
        addRenderableWidget(new TEIntegerSlider(x + half + 6, top + 82, half, 1, ModCommonConfig.ROAD_BUILDER_MAX_ROAD_WIDTH.get(), 1,
            Math.max(1, roadWidth), value -> text("road_width").copy().append(": " + value), value -> { roadWidth = (byte)value; updateResources(true); }));
        int count = RoadType.values().length - 1, cell = w / count;
        for (RoadType type : RoadType.values()) {
            if (type == RoadType.NONE) continue;
            ItemStack icon = new ItemStack(type.getBlock());
            TEButton button = new TEButton(x + materials.size() * cell, top + 131, cell - 3, 24, icon.getHoverName(), b -> {
                roadType = type; updateResources(false);
                for (int i = 0; i < materials.size(); i++) materials.get(i).setSelected(RoadType.values()[i + 1] == roadType);
            }) { @Override protected void renderLabel(GuiGraphics g, int color) { g.renderItem(icon, getX() + (width - 16) / 2, getY() + 4); } };
            button.setSelected(type == roadType); button.setTooltip(Tooltip.create(icon.getHoverName()));
            materials.add(addRenderableWidget(button));
        }
        int footer = top + windowHeight - 30;
        addRenderableWidget(new TEButton(x, footer, 94, 20, text("reset"), b -> {
            ModNetworkManager.RESET_ROAD_BUILDER.send(NetworkDirection.toServer(), new RoadBuilderResetPacket()); onClose();
        })).setTooltip(Tooltip.create(text("tooltip.reset")));
        build = addRenderableWidget(new TEButton(x + (w - 112) / 2, footer, 112, 20, text("build"), b -> {
            updateStackData();
            ModNetworkManager.ROAD_BUILDER_BUILD_ROAD.send(NetworkDirection.toServer(), new RoadBuilderBuildRoadPacket(pos1, pos2, roadWidth, replaceExistingBlocks, roadType));
            RoadConstructionTool.reset(stack);
            ModNetworkManager.RESET_ROAD_BUILDER.send(NetworkDirection.toServer(), new RoadBuilderResetPacket()); onClose();
        }, true));
        build.active = pos1 != null && pos2 != null && roadWidth > 0;
        build.setTooltip(Tooltip.create(text(build.active ? "tooltip.build" : "tooltip.build_missing_pos")));
        addRenderableWidget(new TEButton(left + windowWidth - 88, footer, 74, 20, CommonComponents.GUI_DONE, b -> { updateStackData(); onClose(); }).primary());
        addCloseButton();
    }
    private void updateStackData() {
        var tag = stack.getOrCreateTag();
        tag.putByte(RoadConstructionTool.NBT_ROAD_WIDTH, roadWidth);
        tag.putBoolean(RoadConstructionTool.NBT_REPLACE_BLOCKS, replaceExistingBlocks);
        tag.putInt(RoadConstructionTool.NBT_ROAD_TYPE, roadType.getIndex());
        ModNetworkManager.UPDATE_ROAD_BUILDER.send(NetworkDirection.toServer(), new RoadBuilderDataPacket(replaceExistingBlocks, roadWidth, roadType));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderWindow(g);
        g.drawString(font, text("road_blocks"), left + 14, top + 117, TEColors.MUTED, false);
        TEPanel.draw(g, left + 14, top + 166, windowWidth - 28, 56);
        g.drawString(font, text("required_resources"), left + 22, top + 174, TEColors.MUTED, false);
        if (pos1 != null && pos2 != null) {
            g.renderItem(blockIcon, left + 22, top + 192); g.drawString(font, blockCount, left + 43, top + 196, TEColors.TEXT, false);
            g.renderItem(slopeIcon, left + windowWidth / 2, top + 192); g.drawString(font, slopeCount, left + windowWidth / 2 + 21, top + 196, TEColors.TEXT, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
