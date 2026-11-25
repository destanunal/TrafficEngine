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
import com.destan.trafficengine.item.RoadConstructionUndo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
        if (!(context.getPlayer() instanceof ServerPlayer sender) || sender.isSpectator() || !sender.isAlive()) return;
        final ServerLevel level = sender.serverLevel();
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

        if (sender.getCooldowns().isOnCooldown(item.getItem()) || RoadConstructionUndo.isBuilding(sender)) return;
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

        if (buildingData.blocks.isEmpty()) return;
        var undo = RoadConstructionUndo.start(sender);
        ScheduledTask.create(buildingData, level, RoadConstructionTool.BUILD_DELAY_TICKS, buildingData.blocks.size(),
            (data, task) -> run(data, task, undo));
    }

    private static boolean run(RoadBuildingData data, ScheduledTaskContext context, RoadConstructionUndo.Build undo) {
        ServerPlayer player = (ServerPlayer)data.player;
        ServerLevel level = (ServerLevel)context.level();
        if (!player.isAlive() || player.isSpectator() || player.hasDisconnected() || player.level() != level) {
            return stop(data, undo, null);
        }
        boolean creative = player.isCreative();
        for (Entry<BlockPos, Integer> block : data.blocks.get(context.iteration()).entrySet()) {
            ItemStack tool = player.getItemInHand(data.hand);
            if (tool.isEmpty() || !tool.is(data.toolItem)) return stop(data, undo, "tool_changed");
            BlockPos pos = block.getKey();
            BlockState existing = level.getBlockState(pos);
            if (!data.replaceBlocks && !existing.isAir()) continue;
            if (creative
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
            if (!creative && player.getInventory().countItem(material) < needed) {
                return stop(data, undo, "materials_missing");
            }
            var change = undo.capture(level, pos, existing, replacement, material, creative ? 0 : needed);
            boolean removed = level.destroyBlock(pos, !creative);
            if (!level.setBlockAndUpdate(pos, replacement)) {
                if (removed) undo.record(null);
                continue;
            }
            undo.record(change);
            if (!creative) {
                consumeMaterial(player, material, needed);
                tool.hurtAndBreak(1, player,
                    data.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                if (tool.isEmpty()) return stop(data, undo, "tool_broken");
            }
        }
        if (context.iteration() >= data.blocks.size() - 1) {
            undo.finish();
            player.getCooldowns().removeCooldown(data.toolItem);
            if (level.dimension().location().equals(BuiltinDimensionTypes.NETHER.location())) {
                ModUtils.giveAdvancement(player, TrafficEngine.MOD_ID, "highway_to_hell", "req");
            } else if (level.dimension().location().equals(BuiltinDimensionTypes.END.location())) {
                ModUtils.giveAdvancement(player, TrafficEngine.MOD_ID, "final_destination", "req");
            }
        }
        return true;
    }

    private static void consumeMaterial(ServerPlayer player, Item material, int count) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(material)) continue;
            int removed = Math.min(count, stack.getCount());
            stack.shrink(removed);
            count -= removed;
        }
        player.getInventory().setChanged();
    }

    private static boolean stop(RoadBuildingData data, RoadConstructionUndo.Build undo, String reason) {
        undo.finish();
        data.player.getCooldowns().removeCooldown(data.toolItem);
        if (reason != null) {
            data.player.displayClientMessage(Component.translatable(
                "item.trafficengine.road_construction_tool.stopped." + reason).withStyle(ChatFormatting.YELLOW), true);
        }
        return false;
    }
}
