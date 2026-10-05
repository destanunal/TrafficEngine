package com.destan.trafficengine.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.block.StreetLampBaseBlock;
import com.destan.trafficengine.block.entity.StreetLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Target lamp heads which extend into an adjacent block's otherwise empty space. */
public final class StreetLampTargeting {
    private static WeakReference<Level> cachedLevel = new WeakReference<>(null);
    private static BlockPos cachedEyeBlock;
    private static long cachedTick;
    private static int cachedRadius;
    private static List<BlockPos> candidates = List.of();

    private StreetLampTargeting() {
    }

    public static HitResult pick(Entity camera, double reach, float partialTick, HitResult vanilla) {
        Vec3 from = camera.getEyePosition(partialTick);
        Vec3 to = from.add(camera.getViewVector(partialTick).scale(reach));
        return pick(camera.level(), from, to, CollisionContext.of(camera), vanilla,
            nearbyLamps(camera.level(), from, reach));
    }

    private static List<BlockPos> nearbyLamps(Level level, Vec3 eye, double reach) {
        BlockPos eyeBlock = BlockPos.containing(eye);
        int radius = Mth.ceil(reach) + 2;
        long tick = level.getGameTime();
        if (cachedLevel.get() == level && eyeBlock.equals(cachedEyeBlock)
                && cachedRadius == radius && cachedTick == tick) return candidates;

        List<BlockPos> found = new ArrayList<>();
        int minX = eyeBlock.getX() - radius, maxX = eyeBlock.getX() + radius;
        int minZ = eyeBlock.getZ() - radius, maxZ = eyeBlock.getZ() + radius;
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                // Never load a chunk for a mouse-over query.
                if (!(level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof StreetLampBlockEntity)) continue;
                    BlockPos pos = entity.getBlockPos();
                    if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ
                            && Math.abs(pos.getY() - eyeBlock.getY()) <= radius) found.add(pos);
                }
            }
        }
        cachedLevel = new WeakReference<>(level);
        cachedEyeBlock = eyeBlock;
        cachedRadius = radius;
        cachedTick = tick;
        candidates = List.copyOf(found);
        return candidates;
    }

    public static HitResult pick(BlockGetter level, Vec3 from, Vec3 to, CollisionContext context,
                                 HitResult vanilla, Iterable<BlockPos> positions) {
        HitResult closest = vanilla;
        double distance = from.distanceToSqr(vanilla.getLocation());
        for (BlockPos pos : positions) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof StreetLampBaseBlock)) continue;
            var shape = state.getShape(level, pos, context);
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

    public static BlockHitResult interactionHit(BlockGetter level, BlockHitResult hit) {
        if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof StreetLampBaseBlock)) return hit;
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        Vec3 insideBlock = new Vec3(
            Mth.clamp(location.x, pos.getX(), pos.getX() + 1),
            Mth.clamp(location.y, pos.getY(), pos.getY() + 1),
            Mth.clamp(location.z, pos.getZ(), pos.getZ() + 1));
        if (insideBlock.equals(location)) return hit;
        // Vanilla servers require the click coordinate to be close to its owning block.
        // Keep the visual ray hit intact and only normalize the interaction argument.
        return new BlockHitResult(insideBlock, hit.getDirection(), pos, hit.isInside());
    }
}
