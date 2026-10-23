import com.destan.trafficengine.block.ChevronPanelBlock;
import com.destan.trafficengine.block.TemporaryPedestrianFenceBlock;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.packets.cts.ChevronPanelPacket;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Headless checks of actual block states, neighbour events and cached shapes; no packaged mod. */
public final class RoadsideConnectionRegressionCheck {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static int fluidTicks;
    private static volatile VoxelShape sink;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        // This standalone JVM has already bootstrapped/frozen vanilla registries. Permit
        // two unregistered fixtures here, without modifying any game files or registries on disk.
        var holders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"); holders.setAccessible(true);
        var frozen = MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        boolean wasFrozen = frozen.getBoolean(BuiltInRegistries.BLOCK); frozen.setBoolean(BuiltInRegistries.BLOCK, false);
        Object previous = holders.get(BuiltInRegistries.BLOCK); holders.set(BuiltInRegistries.BLOCK, new IdentityHashMap<>());
        ChevronPanelBlock panel;
        TemporaryPedestrianFenceBlock fence;
        try { panel = new ChevronPanelBlock(); fence = new TemporaryPedestrianFenceBlock(); }
        finally { holders.set(BuiltInRegistries.BLOCK, previous); frozen.setBoolean(BuiltInRegistries.BLOCK, wasFrozen); }
        check(!((Object)panel instanceof EntityBlock) && !((Object)fence instanceof EntityBlock), "No new ticking block entities");
        check(panel.defaultBlockState().getValue(ChevronPanelBlock.RED_WHITE), "New signs default to red/white");
        check(panel.defaultBlockState().getValue(ChevronPanelBlock.ARROW_RIGHT), "New signs point right");
        check(!panel.defaultBlockState().getValue(ChevronPanelBlock.SINGLE), "Keep the existing triple sign as the default model");
        LevelAccessor events = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class}, (proxy, method, values) -> {
            if (method.getName().equals("scheduleTick")) { fluidTicks++; return null; }
            throw new AssertionError("Neighbour updates must not scan the world: " + method.getName());
        });
        CollisionContext context = CollisionContext.empty();
        BlockPos pos = new BlockPos(8, 64, -3);
        int fenceChecks = 0, panelChecks = 0;
        for (BlockState state : fence.getStateDefinition().getPossibleStates()) {
            int mask = TemporaryPedestrianFenceBlock.connectionMask(state);
            VoxelShape outline = fence.getShape(state, null, pos, context), collision = fence.getCollisionShape(state, null, pos, context);
            check(outline == fence.getShape(state.cycle(TemporaryPedestrianFenceBlock.WATERLOGGED), null, pos, context), "Water must reuse the outline cache");
            check(outline.bounds().maxY == 2 && collision.bounds().maxY == 2, "Two-block visible height and matching collision");
            check(outline == collision, "Tall barrier outline and collision share one cache");
            check(!outline.toAabbs().stream().anyMatch(b -> b.minY == 0 && (b.maxX-b.minX) > .26 && (b.maxZ-b.minZ) > .26), "Black foot removed from hitbox");
            check(fence.getFluidState(state).getType() == (state.getValue(TemporaryPedestrianFenceBlock.WATERLOGGED) ? Fluids.WATER : Fluids.EMPTY), "Fence water state");
            for (Direction side : SIDES) {
                for (boolean present : new boolean[]{false, true}) {
                    fluidTicks = 0;
                    BlockState changed = fence.updateShape(state, side, present ? fence.defaultBlockState() : Blocks.AIR.defaultBlockState(), events, pos, pos.relative(side));
                    check(changed.getValue(TemporaryPedestrianFenceBlock.property(side)) == present, "Only this neighbour controls its connection");
                    for (Direction other : SIDES) if (other != side)
                        check(changed.getValue(TemporaryPedestrianFenceBlock.property(other)) == state.getValue(TemporaryPedestrianFenceBlock.property(other)), "Do not clear other fence connections");
                    check(fluidTicks == (state.getValue(TemporaryPedestrianFenceBlock.WATERLOGGED) ? 1 : 0), "Only waterlogged updates schedule a fluid tick");
                    fenceChecks++;
                }
            }
            for (Rotation rotation : Rotation.values()) {
                BlockState rotated = fence.rotate(state, rotation);
                for (Direction side : SIDES)
                    check(rotated.getValue(TemporaryPedestrianFenceBlock.property(rotation.rotate(side))) == state.getValue(TemporaryPedestrianFenceBlock.property(side)), "Fence connection rotation");
            }
            BlockState fourTurns = state;
            for (int i = 0; i < 4; i++) fourTurns = fence.rotate(fourTurns, Rotation.CLOCKWISE_90);
            check(fourTurns == state, "Four fence rotations restore all state");
            for (Mirror mirror : Mirror.values()) check(fence.mirror(fence.mirror(state, mirror), mirror) == state, "Fence mirror round trip");
            check(mask > 0 && mask < 16, "Isolated sections use their placed orientation");
        }
        for (BlockState state : panel.getStateDefinition().getPossibleStates()) {
            Direction facing = state.getValue(ChevronPanelBlock.FACING);
            VoxelShape outline = panel.getShape(state, null, pos, context);
            check(outline.bounds().maxY == 14/16.0, "Shorter pole lowers the board without shrinking it");
            check(state.getLightEmission() == 7, "All sign settings use constant low block light, without day/night ticks");
            check(outline == panel.getShape(state.cycle(ChevronPanelBlock.ARROW_RIGHT).cycle(ChevronPanelBlock.RED_WHITE), null, pos, context), "Settings reuse panel shapes");
            boolean single = state.getValue(ChevronPanelBlock.SINGLE);
            double span = (single ? 8 : 16) / 16.0;
            check((facing.getAxis() == Direction.Axis.Z ? outline.bounds().getXsize() : outline.bounds().getZsize()) == span, "Square sign has a square board hitbox");
            for (Direction side : SIDES) for (Direction neighborFacing : SIDES) for (boolean neighborSingle : new boolean[]{false, true}) {
                BlockState neighbor = panel.defaultBlockState().setValue(ChevronPanelBlock.FACING, neighborFacing).setValue(ChevronPanelBlock.SINGLE, neighborSingle);
                BlockState changed = panel.updateShape(state, side, neighbor, events, pos, pos.relative(side));
                boolean joined = !single && !neighborSingle && facing == neighborFacing;
                if (side == facing.getClockWise()) check(changed.getValue(ChevronPanelBlock.LEFT) == joined, "Only adjacent wide panels share left caps");
                else if (side == facing.getCounterClockWise()) check(changed.getValue(ChevronPanelBlock.RIGHT) == joined, "Only adjacent wide panels share right caps");
                else check(changed == state, "Unrelated panel neighbours leave settings and joins intact");
                panelChecks++;
            }
            for (Mirror mirror : Mirror.values()) check(panel.mirror(panel.mirror(state, mirror), mirror) == state, "Panel mirror round trip");
            BlockState fourTurns = state;
            for (int i = 0; i < 4; i++) fourTurns = panel.rotate(fourTurns, Rotation.CLOCKWISE_90);
            check(fourTurns == state, "Four panel rotations restore all state");
        }
        for (Direction facing : SIDES) {
            BlockState wide = panel.defaultBlockState().setValue(ChevronPanelBlock.FACING, facing)
                .setValue(ChevronPanelBlock.LEFT, true).setValue(ChevronPanelBlock.RIGHT, true);
            BlockGetter noReads = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class}, (proxy, method, values) -> {
                throw new AssertionError("Changing to single, or retaining size, must not read the world");
            });
            BlockState single = ChevronPanelBlock.withSingle(wide, true, noReads, pos);
            check(single.getValue(ChevronPanelBlock.SINGLE) && !single.getValue(ChevronPanelBlock.LEFT) && !single.getValue(ChevronPanelBlock.RIGHT), "Single model clears both joins immediately");
            check(ChevronPanelBlock.withSingle(single, true, noReads, pos) == single, "Same model is a no-op");
            int[] reads = {0};
            BlockGetter neighbors = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class}, (proxy, method, values) -> {
                check(method.getName().equals("getBlockState"), "Read adjacent block states only");
                BlockPos target = (BlockPos)values[0]; reads[0]++;
                if (target.equals(pos.relative(facing.getClockWise()))) return wide;
                check(target.equals(pos.relative(facing.getCounterClockWise())), "Read only the two adjacent positions");
                return single;
            });
            BlockState restored = ChevronPanelBlock.withSingle(single, false, neighbors, pos);
            check(reads[0] == 2 && restored.getValue(ChevronPanelBlock.LEFT) && !restored.getValue(ChevronPanelBlock.RIGHT), "Restoring wide recalculates both joins and ignores neighboring single signs");
        }
        for (boolean right : new boolean[]{false, true}) for (boolean red : new boolean[]{false, true}) for (boolean single : new boolean[]{false, true}) {
            ChevronPanelPacket sent = new ChevronPanelPacket(pos, right, red, single), received = new ChevronPanelPacket(PacketStatus.EMPTY);
            CompoundTag packet = sent.serializeNbt(); received.deserializeNbt(packet);
            check(packet.equals(received.serializeNbt()), "Chevron position, theme, model and direction packet round trip");
        }
        BlockState[] states = fence.getStateDefinition().getPossibleStates().toArray(BlockState[]::new);
        for (int i = 0; i < 200_000; i++) sink = fence.getShape(states[i & 63], null, pos, context);
        var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId(), before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 1_000_000; i++) {
                sink = fence.getShape(states[i & 63], null, pos, context);
                sink = fence.getCollisionShape(states[i & 63], null, pos, context);
            }
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            check(allocated < 4096, "Cached shape lookups must not allocate per call: " + allocated);
            System.out.println("Shape lookup allocation sample: " + allocated + " bytes across 2,000,000 lookups (not an FPS measurement).");
        }
        System.out.println("PASS: " + fenceChecks + " fence neighbour changes, " + panelChecks
            + " panel neighbour cases; rotation/mirror, water, packet and cache checks.");
    }
}
