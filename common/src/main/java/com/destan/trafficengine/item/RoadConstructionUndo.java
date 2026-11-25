package com.destan.trafficengine.item;

import com.destan.trafficengine.block.BoomBarrierPartBlock;
import com.destan.trafficengine.block.CurvedTrafficPoleBlock;
import com.destan.trafficengine.block.LedDevicePartBlock;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.blueprint.BlueprintPlan;
import com.destan.trafficengine.blueprint.BlueprintUndo;
import dev.architectury.event.events.common.LifecycleEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** One server-side undo record per recent builder, with no idle polling. */
public final class RoadConstructionUndo {
    private static final int MAX_PLAYERS = 16;
    private static final int MAX_DATA_BYTES = 128 * 1024;
    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<UUID, Operation> HISTORY = new LinkedHashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    static {
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            HISTORY.clear();
            ACTIVE.clear();
        });
    }

    private RoadConstructionUndo() {}

    public record Change(BlockPos pos, BlockState before, CompoundTag data, BlockState after,
                         Item placedMaterial, int spent) {}

    private record Operation(ResourceKey<Level> dimension, List<Change> changes, boolean blocked) {}

    public static final class Build {
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final List<Change> changes = new ArrayList<>();
        private int dataBytes;
        private boolean blocked;
        private boolean changed;

        private Build(UUID owner, ResourceKey<Level> dimension) {
            this.owner = owner;
            this.dimension = dimension;
        }

        public Change capture(ServerLevel level, BlockPos pos, BlockState before, BlockState after,
                              Item material, int spent) {
            if (blocked) return null;
            Block block = before.getBlock();
            // Linked structures and container inventories cannot be recreated from one cell.
            if (before.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    || before.hasProperty(BlockStateProperties.BED_PART)
                    || block instanceof CurvedTrafficPoleBlock || block instanceof LedDevicePartBlock
                    || block instanceof BoomBarrierPartBlock) return null;
            CompoundTag data = null;
            if (before.hasBlockEntity()) {
                if (!(block instanceof RoadBlock)) return null;
                data = BlueprintUndo.snapshot(level, pos);
                if (data == null || data.sizeInBytes() > MAX_DATA_BYTES - dataBytes) return null;
            }
            return new Change(pos.immutable(), before, data, after, material, spent);
        }

        public void record(Change change) {
            changed = true;
            if (change == null) {
                blocked = true;
                changes.clear();
            } else {
                changes.add(change);
                if (change.data != null) dataBytes += change.data.sizeInBytes();
            }
        }

        public void finish() {
            ACTIVE.remove(owner);
            if (!changed) return;
            HISTORY.remove(owner);
            if (HISTORY.size() >= MAX_PLAYERS) HISTORY.remove(HISTORY.keySet().iterator().next());
            HISTORY.put(owner, new Operation(dimension, blocked ? List.of() : changes, blocked));
        }
    }

    public static boolean isBuilding(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static Build start(ServerPlayer player) {
        UUID owner = player.getUUID();
        ACTIVE.add(owner);
        return new Build(owner, player.level().dimension());
    }

    public static void undo(ServerPlayer player, ItemStack tool) {
        if (!player.isAlive() || player.isSpectator()) return;
        UUID owner = player.getUUID();
        if (isBuilding(player)) {
            message(player, "busy");
            return;
        }
        Operation operation = HISTORY.get(owner);
        if (operation == null) {
            message(player, "empty");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(operation.dimension)) {
            message(player, "dimension");
            return;
        }
        if (operation.blocked) {
            message(player, "blocked");
            return;
        }

        Map<Item, Integer> materials = new LinkedHashMap<>();
        for (Change change : operation.changes) {
            if (!level.isLoaded(change.pos) || !level.mayInteract(player, change.pos)
                    || !player.mayUseItemAt(change.pos, Direction.UP, tool)
                    || level.getBlockState(change.pos) != change.after) {
                message(player, "unavailable");
                return;
            }
            if (change.spent > 0 && !accountMaterials(change, materials)) {
                message(player, "blocked");
                return;
            }
        }
        Inventory inventory = player.getInventory();
        for (var entry : materials.entrySet()) {
            if (entry.getValue() <= 0) continue;
            int missing = entry.getValue() - countMaterial(inventory, new ItemStack(entry.getKey()));
            if (missing > 0) {
                message(player, "materials", missing, entry.getKey().getDescription());
                return;
            }
        }

        int restored = 0;
        for (Change change : operation.changes) {
            if (!level.setBlock(change.pos, change.before, WRITE_FLAGS)) {
                rollback(level, operation.changes, restored);
                message(player, "unavailable");
                return;
            }
            restored++;
            if (change.data == null) continue;
            var entity = level.getBlockEntity(change.pos);
            if (entity == null) {
                rollback(level, operation.changes, restored);
                message(player, "unavailable");
                return;
            }
            entity.load(change.data.copy());
            entity.setChanged();
            level.sendBlockUpdated(change.pos, change.after, change.before, Block.UPDATE_CLIENTS);
        }

        HISTORY.remove(owner);
        for (var entry : materials.entrySet()) {
            int amount = entry.getValue();
            ItemStack stack = new ItemStack(entry.getKey());
            if (amount > 0) consumeMaterial(inventory, stack, amount);
            while (amount < 0) {
                int count = Math.min(-amount, stack.getMaxStackSize());
                var refund = new ItemStack(entry.getKey(), count);
                inventory.add(refund);
                if (!refund.isEmpty()) player.drop(refund, false);
                amount += count;
            }
        }
        if (!materials.isEmpty()) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        updateNeighbors(level, operation.changes, restored);
        message(player, "success", restored);
    }

    private static boolean accountMaterials(Change change, Map<Item, Integer> materials) {
        BlockState before = change.before;
        if (!before.isAir() && !(before.getBlock() instanceof LiquidBlock)) {
            Item item = BlueprintPlan.material(before);
            if (before.is(Blocks.GRASS_BLOCK) || before.is(Blocks.DIRT_PATH) || before.is(Blocks.FARMLAND)
                    || before.is(Blocks.PODZOL) || before.is(Blocks.MYCELIUM)) item = Items.DIRT;
            else if (before.is(Blocks.STONE)) item = Items.COBBLESTONE;
            else if (before.is(Blocks.DEEPSLATE)) item = Items.COBBLED_DEEPSLATE;
            if (item == Items.AIR) return false;
            // Survival building drops the displaced block. Replacing it consumes its material.
            materials.merge(item, BlueprintPlan.cost(before), Integer::sum);
        }
        materials.merge(change.placedMaterial, -change.spent, Integer::sum);
        return true;
    }

    private static int countMaterial(Inventory inventory, ItemStack material) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, material)) count += stack.getCount();
        }
        return count;
    }

    private static void consumeMaterial(Inventory inventory, ItemStack material, int amount) {
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(ItemStack.isSameItemSameTags(stack, material))) continue;
            int used = Math.min(amount, stack.getCount());
            stack.shrink(used);
            amount -= used;
        }
    }

    private static void rollback(ServerLevel level, List<Change> changes, int restored) {
        for (int i = restored - 1; i >= 0; i--) {
            Change change = changes.get(i);
            level.setBlock(change.pos, change.after, WRITE_FLAGS);
        }
        updateNeighbors(level, changes, restored);
    }

    private static void updateNeighbors(ServerLevel level, List<Change> changes, int restored) {
        for (int i = 0; i < restored; i++) {
            Change change = changes.get(i);
            BlockState state = level.getBlockState(change.pos);
            state.updateNeighbourShapes(level, change.pos, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.updateNeighborsAt(change.pos, state.getBlock());
        }
    }

    private static void message(ServerPlayer player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("gui.trafficengine.road_builder.undo." + key, arguments), true);
    }
}
