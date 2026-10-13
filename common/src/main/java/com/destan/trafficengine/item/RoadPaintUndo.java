package com.destan.trafficengine.item;
import com.destan.trafficengine.util.ItemData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import com.destan.trafficengine.Constants;
import com.destan.trafficengine.block.ConnectedRoadBlock;
import com.destan.trafficengine.data.PaintColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One bounded, server-side paint transaction per player. */
public final class RoadPaintUndo {
    private static final String BRUSH_ID = "road_paint_brush_id";
    private static final int MAX_SNAPSHOTS = RoadPaintPlan.MAX_CELLS * 5;
    private static final Map<ServerPlayer, Operation> HISTORY = new WeakHashMap<>();

    private RoadPaintUndo() {}

    record Snapshot(BlockState state, CompoundTag data) {
        Snapshot { data = data == null ? null : data.copy(); }
    }

    static final class Entry {
        final BlockPos pos;
        final Snapshot before, after;
        final boolean replacement;
        final Set<String> keys;

        Entry(BlockPos pos, Snapshot before, Snapshot after) {
            this.pos = pos.immutable(); this.before = before; this.after = after;
            replacement = !before.state.equals(after.state) || before.data == null || after.data == null;
            Set<String> changed = new LinkedHashSet<>();
            if (!replacement) {
                changed.addAll(before.data.getAllKeys()); changed.addAll(after.data.getAllKeys());
                changed.removeIf(key -> Objects.equals(before.data.get(key), after.data.get(key)));
            }
            keys = Set.copyOf(changed);
        }

        CompoundTag restoredData(Snapshot current) {
            if (replacement || !after.state.equals(current.state) || current.data == null)
                return before.data == null ? null : before.data.copy();
            CompoundTag restored = current.data.copy();
            for (String key : keys) {
                if (before.data.contains(key)) restored.put(key, before.data.get(key).copy());
                else restored.remove(key);
            }
            return restored;
        }
    }

    private record Operation(ResourceKey<Level> dimension, List<Entry> entries, UUID brushId,
            PaintColor color, int paintSpent) {}

    public static final class Transaction {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final ItemStack brush;
        private final Map<BlockPos, Snapshot> before;
        private final int paintBefore;
        private final PaintColor color;

        private Transaction(ServerLevel level, ServerPlayer player, ItemStack brush, Map<BlockPos, Snapshot> before) {
            this.level = level; this.player = player; this.brush = brush; this.before = before;
            paintBefore = BrushItem.getPaint(brush); color = BrushItem.getColor(brush);
        }

        private void finish() {
            List<Entry> entries = new ArrayList<>();
            before.forEach((pos, previous) -> {
                Snapshot after = snapshot(level, pos);
                if (!previous.equals(after)) entries.add(new Entry(pos, previous, after));
            });
            if (entries.isEmpty()) return;
            int spent = Math.max(0, paintBefore - BrushItem.getPaint(brush));
            UUID id = null;
            if (spent > 0) {
                CompoundTag tag = ItemData.getOrCreate(brush);
                if (!tag.hasUUID(BRUSH_ID)) tag.putUUID(BRUSH_ID, UUID.randomUUID());
                id = tag.getUUID(BRUSH_ID);
                ItemData.set(brush, tag);
            }
            HISTORY.put(player, new Operation(level.dimension(), List.copyOf(entries), id, color, spent));
        }
    }

    public static Transaction begin(Level level, Player player, ItemStack brush, Collection<BlockPos> positions) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sender)
                || positions.isEmpty() || positions.size() > RoadPaintPlan.MAX_CELLS) return null;
        Set<BlockPos> affected = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            affected.add(pos.immutable());
            // Reconnecting painted roads can also change their immediate neighbours.
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos neighbour = pos.relative(side);
                if (level.isLoaded(neighbour) && level.getBlockState(neighbour).getBlock() instanceof ConnectedRoadBlock)
                    affected.add(neighbour);
            }
        }
        if (affected.size() > MAX_SNAPSHOTS) return null;
        Map<BlockPos, Snapshot> before = new LinkedHashMap<>();
        for (BlockPos pos : affected) before.put(pos, snapshot(serverLevel, pos));
        return new Transaction(serverLevel, sender, brush, before);
    }

    public static void finish(Transaction transaction) { if (transaction != null) transaction.finish(); }

    private static Snapshot snapshot(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return new Snapshot(level.getBlockState(pos), entity == null ? null : entity.saveWithoutMetadata(level.registryAccess()));
    }

    public static void undo(ServerPlayer player, ItemStack heldBrush) {
        Operation operation = HISTORY.get(player);
        if (operation == null) { message(player, "undo_empty"); return; }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(operation.dimension)) { message(player, "undo_dimension"); return; }
        Map<BlockPos, CompoundTag> restored = new LinkedHashMap<>();
        for (Entry entry : operation.entries) {
            if (!level.isLoaded(entry.pos) || !level.mayInteract(player, entry.pos)
                    || !player.mayUseItemAt(entry.pos, Direction.UP, heldBrush)) {
                message(player, "undo_unavailable"); return;
            }
            Snapshot current = snapshot(level, entry.pos);
            restored.put(entry.pos, entry.restoredData(current));
        }
        // Restore all states and data before reconnecting, so no partial road layout is observed.
        for (Entry entry : operation.entries)
            level.setBlock(entry.pos, entry.before.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (Entry entry : operation.entries) {
            CompoundTag data = restored.get(entry.pos);
            BlockEntity entity = level.getBlockEntity(entry.pos);
            if (data != null && entity != null) {
                entity.loadWithComponents(data,level.registryAccess()); entity.setChanged();
            }
            level.sendBlockUpdated(entry.pos, entry.after.state, entry.before.state, Block.UPDATE_ALL);
        }
        for (Entry entry : operation.entries) level.updateNeighborsAt(entry.pos, entry.before.state.getBlock());
        HISTORY.remove(player);
        refund(player, operation);
        ItemData.edit(heldBrush,RoadPainting::clearSelection);
        player.getInventory().setChanged();
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.5F);
        message(player, "undo_done");
    }

    private static void refund(ServerPlayer player, Operation operation) {
        if (operation.brushId == null || operation.paintSpent == 0) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack brush = player.getInventory().getItem(slot);
            CompoundTag tag = ItemData.get(brush);
            if (brush.getItem() instanceof BrushItem && tag != null && tag.hasUUID(BRUSH_ID)
                    && tag.getUUID(BRUSH_ID).equals(operation.brushId) && BrushItem.getColor(brush) == operation.color) {
                tag.putInt(BrushItem.NBT_PAINT, Math.min(Constants.MAX_PAINT, BrushItem.getPaint(brush) + operation.paintSpent));
                ItemData.set(brush,tag);
                return;
            }
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush." + key), true);
    }
}
