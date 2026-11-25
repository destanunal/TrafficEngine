package com.destan.trafficengine.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.block.StreetLampBaseBlock;
import com.destan.trafficengine.block.StreetSignBlock;
import com.destan.trafficengine.block.TrafficLightRequestButtonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Target static equipment that extends outside the block storing it. */
public final class StreetLampTargeting {
    private static WeakReference<Level> cachedLevel = new WeakReference<>(null);
    private static int cachedX, cachedY, cachedZ;
    private static long cachedTick;
    private static int cachedRadius;
    private static List<BlockPos> candidates = List.of();

    private StreetLampTargeting() {
    }

    public static HitResult pick(Entity camera, double reach, float partialTick, HitResult vanilla) {
        Vec3 from = camera.getEyePosition(partialTick);
        Level level = camera.level();
        List<BlockPos> nearby = nearbyEquipment(level, from, reach);
        if (nearby.isEmpty()) return vanilla;
        Vec3 to = from.add(camera.getViewVector(partialTick).scale(reach));
        return pick(level, from, to, CollisionContext.of(camera), vanilla, nearby);
    }

    private static List<BlockPos> nearbyEquipment(Level level, Vec3 eye, double reach) {
        int eyeX = Mth.floor(eye.x), eyeY = Mth.floor(eye.y), eyeZ = Mth.floor(eye.z);
        int radius = Mth.ceil(reach) + 2;
        long tick = level.getGameTime();
        boolean sameLevel = cachedLevel.get() == level;
        if (sameLevel && eyeX == cachedX && eyeY == cachedY && eyeZ == cachedZ
                && cachedRadius == radius && cachedTick == tick) return candidates;

        List<BlockPos> found = null;
        int minX = eyeX - radius, maxX = eyeX + radius;
        int minZ = eyeZ - radius, maxZ = eyeZ + radius;
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                // Never load a chunk for a mouse-over query.
                if (!(level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!extended(entity.getBlockState())) continue;
                    BlockPos pos = entity.getBlockPos();
                    if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ
                            && Math.abs(pos.getY() - eyeY) <= radius) {
                        if (found == null) found = new ArrayList<>();
                        found.add(pos);
                    }
                }
            }
        }
        if (!sameLevel) cachedLevel = new WeakReference<>(level);
        cachedX = eyeX;
        cachedY = eyeY;
        cachedZ = eyeZ;
        cachedRadius = radius;
        cachedTick = tick;
        candidates = found == null ? List.of() : found;
        return candidates;
    }

    public static HitResult pick(BlockGetter level, Vec3 from, Vec3 to, CollisionContext context,
                                 HitResult vanilla, Iterable<BlockPos> positions) {
        HitResult closest = vanilla;
        double distance = from.distanceToSqr(vanilla.getLocation());
        for (BlockPos pos : positions) {
            var state = level.getBlockState(pos);
            if (!extended(state)) continue;
            // Only the housing may claim a hit in the lower cell. The pole there
            // keeps its own target, even when the button is mounted on it.
            var shape = state.getBlock() instanceof TrafficLightRequestButtonBlock
                ? TrafficLightRequestButtonBlock.housingShape(state) : state.getShape(level, pos, context);
            BlockHitResult hit = shape.clip(from, to, pos);
            if (hit == null) continue;
            double candidateDistance = from.distanceToSqr(hit.getLocation());
            // Preserve nearer walls, fluids and any other normal block target.
            if (candidateDistance < distance - 1.0E-7) {
                closest = hit;
                distance = candidateDistance;
            }
        }
        return closest;
    }

    private static boolean extended(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof StreetLampBaseBlock || state.getBlock() instanceof StreetSignBlock
            || state.getBlock() instanceof TrafficLightRequestButtonBlock && state.getValue(TrafficLightRequestButtonBlock.SINK) > 0
            || state.getBlock() instanceof com.destan.trafficengine.block.WarningLightBlock && state.getValue(com.destan.trafficengine.block.WarningLightBlock.COMPACT);
    }
    public static BlockHitResult interactionHit(BlockGetter level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!extended(state)) return hit;
        Vec3 location = hit.getLocation();
        double x = Mth.clamp(location.x, pos.getX(), pos.getX() + 1);
        // A fully lowered button sits below its own cell. The server accepts up
        // to half a block below it, which is still within the shifted housing.
        double bottom = state.getBlock() instanceof TrafficLightRequestButtonBlock ? pos.getY() - 0.5 : pos.getY();
        double y = Mth.clamp(location.y, bottom, pos.getY() + 1);
        double z = Mth.clamp(location.z, pos.getZ(), pos.getZ() + 1);
        if (Double.compare(x, location.x) == 0 && Double.compare(y, location.y) == 0
                && Double.compare(z, location.z) == 0) return hit;
        // Vanilla servers require the click coordinate to be close to its owning block.
        // Keep the visual ray hit intact and only normalize the interaction argument.
        return new BlockHitResult(new Vec3(x, y, z), hit.getDirection(), pos, hit.isInside());
    }
}
