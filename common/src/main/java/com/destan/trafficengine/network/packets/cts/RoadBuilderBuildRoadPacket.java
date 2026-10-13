package com.destan.trafficengine.network.packets.cts;

import java.util.Map.Entry;
import com.destan.trafficengine.config.ModCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.ModUtils;
import com.destan.trafficengine.util.ScheduledTask;
import com.destan.trafficengine.util.ScheduledTask.ScheduledTaskContext;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.AsphaltSlope;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.item.RoadConstructionTool.RoadBuildingData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

public class RoadBuilderBuildRoadPacket extends NetworkPacketData {

    private static final String NBT_POS1 = "Pos1";
    private static final String NBT_POS2 = "Pos2";
    private static final String NBT_ROAD_WIDTH = "RoadWidth";
    private static final String NBT_REAPLCE_BLOCKS = "ReplaceBlocks";
    private static final String NBT_ROAD_TYPE = "RoadType";

    private WorldLocation pos1;
    private WorldLocation pos2;
    private byte roadWidth;
    private boolean replaceBlocks;
    private RoadType roadType;

    public RoadBuilderBuildRoadPacket(PacketStatus status) {
        super(status);
    }
    
    public RoadBuilderBuildRoadPacket(WorldLocation pos1, WorldLocation pos2, byte roadWidth, boolean replaceBlocks, RoadType roadType) {
        super(PacketStatus.OK);
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.roadWidth = roadWidth;
        this.replaceBlocks = replaceBlocks;
        this.roadType = roadType;
    }    

    @Override
    protected void write(CompoundTag nbt) {
        nbt.put(NBT_POS1, pos1.toNbt());
        nbt.put(NBT_POS2, pos2.toNbt());
        nbt.putByte(NBT_ROAD_WIDTH, roadWidth);
        nbt.putBoolean(NBT_REAPLCE_BLOCKS, replaceBlocks);
        nbt.putInt(NBT_ROAD_TYPE, roadType.getIndex());
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos1 = WorldLocation.loadFromNbt(nbt.getCompound(NBT_POS1));
        this.pos2 = WorldLocation.loadFromNbt(nbt.getCompound(NBT_POS2));
        this.roadWidth = nbt.getByte(NBT_ROAD_WIDTH);
        this.replaceBlocks = nbt.getBoolean(NBT_REAPLCE_BLOCKS);
        this.roadType = RoadType.getRoadTypeByIndex(nbt.getInt(NBT_ROAD_TYPE));
    }

    public static void handle(RoadBuilderBuildRoadPacket packet, NetworkPacketContext context) {        
        if (!(context.getPlayer() instanceof ServerPlayer sender)) return;
        final Level level = sender.level();
        ItemStack item = null;
        InteractionHand hand = null;

        if (sender.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof RoadConstructionTool) {
            item = sender.getItemInHand(InteractionHand.MAIN_HAND);
            hand = InteractionHand.MAIN_HAND;
        } else if (sender.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof RoadConstructionTool) {
            item = sender.getItemInHand(InteractionHand.OFF_HAND);
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }

        if (sender.getCooldowns().isOnCooldown(item.getItem())) return;
        if (packet.pos1 == null || packet.pos2 == null || packet.roadType == null
                || packet.roadType == RoadType.NONE || packet.roadWidth < 1
                || packet.roadWidth > ModCommonConfig.ROAD_BUILDER_MAX_ROAD_WIDTH.get()
                || !packet.pos1.dimension.equals(level.dimension().location())
                || !packet.pos2.dimension.equals(level.dimension().location())) return;
        PacketStatus validity = RoadConstructionTool.isLineValid(packet.pos1.getLocationVec3(), packet.pos2.getLocationVec3());
        if (validity.flag() != PacketStatus.FLAG_OK) {
            sender.displayClientMessage(Component.translatable(validity.code() == 1
                ? "item.trafficengine.road_construction_tool.status_too_far"
                : "item.trafficengine.road_construction_tool.status_slope_too_steep").withStyle(ChatFormatting.RED), true);
            return;
        }

        final RoadBuildingData buildingData = RoadConstructionTool.prepareRoadBuilding(
            level,
            sender,
            hand,
            item,
            packet.pos1.getLocationVec3(),
            packet.pos2.getLocationVec3(), 
            packet.roadWidth,
            packet.replaceBlocks,
            packet.roadType
        );

        ScheduledTask.create(buildingData, level, RoadConstructionTool.BUILD_DELAY_TICKS, buildingData.blocks.size(), packet::run);
    }

    private boolean run(RoadBuildingData data, ScheduledTaskContext context) {
        if (!data.player.isAlive() || data.player.level() != context.level()) {
            data.player.getCooldowns().removeCooldown(data.toolItem);
            return false;
        }
        for (Entry<BlockPos, Integer> block : data.blocks.get(context.iteration()).entrySet()) {
            ItemStack tool = data.player.getItemInHand(data.hand);
            if (tool.isEmpty() || !tool.is(data.toolItem)) return stop(data, "tool_changed");
            BlockState existing = context.level().getBlockState(block.getKey());
            if (!data.replaceBlocks && !existing.isAir()) continue;
            if (isPlayerCreative(data.player)
                    ? existing.getBlock().defaultDestroyTime() == Block.INDESTRUCTIBLE
                    : !((RoadConstructionTool)tool.getItem()).canReplaceBlock(existing)) continue;

            int layers = block.getValue();
            boolean slope = layers > 0 && layers < 8;
            Item material = (slope ? data.roadType.getSlope() : data.roadType.getBlock()).asItem();
            int needed = slope ? layers : 1;
            BlockState replacement = slope
                ? data.roadType.getSlope().defaultBlockState().setValue(AsphaltSlope.LAYERS, layers)
                : data.roadType.getBlock().defaultBlockState();
            if (existing.equals(replacement)) continue;
            if (!isPlayerCreative(data.player) && data.player.getInventory().countItem(material) < needed) {
                return stop(data, "materials_missing");
            }
            context.level().destroyBlock(block.getKey(), !isPlayerCreative(data.player));
            context.level().setBlockAndUpdate(block.getKey(), replacement);
            if (!isPlayerCreative(data.player)) {
                consumeMaterial(data.player, material, needed);
                tool.hurtAndBreak(1, data.player,
                    data.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                if (tool.isEmpty()) return stop(data, "tool_broken");
            }
        }
        if (context.iteration() >= data.blocks.size() - 1) {
            data.player.getCooldowns().removeCooldown(data.toolItem);
            if (context.level().dimension().location().equals(BuiltinDimensionTypes.NETHER.location())) {
                ModUtils.giveAdvancement((ServerPlayer)data.player, TrafficEngine.MOD_ID, "highway_to_hell", "req");
            } else if (context.level().dimension().location().equals(BuiltinDimensionTypes.END.location())) {
                ModUtils.giveAdvancement((ServerPlayer)data.player, TrafficEngine.MOD_ID, "final_destination", "req");
            }
        }
        return true;
    }

    private static void consumeMaterial(Player player, Item material, int count) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(material)) continue;
            int removed = Math.min(count, stack.getCount());
            stack.shrink(removed);
            count -= removed;
        }
        player.getInventory().setChanged();
    }

    private static boolean stop(RoadBuildingData data, String reason) {
        data.player.getCooldowns().removeCooldown(data.toolItem);
        data.player.displayClientMessage(Component.translatable(
            "item.trafficengine.road_construction_tool.stopped." + reason).withStyle(ChatFormatting.YELLOW), true);
        return false;
    }

    private static boolean isPlayerCreative(Player pPlayer) {
        return pPlayer.isCreative() || pPlayer.isSpectator();
    }
}
