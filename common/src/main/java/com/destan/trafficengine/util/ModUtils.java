package com.destan.trafficengine.util;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

public final class ModUtils {
    private ModUtils() {}
    public static ResourceLocation resourceLocation(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    public static ResourceLocation resourceLocation(String id) { return ResourceLocation.parse(id); }
    public static <T> void doIfNotNull(T value, Consumer<T> action) { if (value != null) action.accept(value); }
    public static int coordsToInt(byte x, byte y) { return (x & 255) << 16 | (y & 255); }
    public static byte[] intToCoords(int value) { return new byte[]{(byte)(value >>> 16), (byte)value}; }
    public static double slope(Vector3f a, Vector3f b) { float dx = a.x - b.x, dz = a.z - b.z; return Math.sqrt(dx * dx + dz * dz) / Math.abs(a.y - b.y); }
    public static boolean rotateBlock(Level level, BlockPos pos, Rotation rotation) {
        var before = level.getBlockState(pos);
        var after = before.rotate(rotation);
        if (before == after) return false;
        level.setBlockAndUpdate(pos, after);
        for (var direction : net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING.getPossibleValues()) {
            var adjacent = pos.relative(direction);
            var updated = after.updateShape(direction, level.getBlockState(adjacent), level, pos, adjacent);
            if (updated != after) {
                if (updated.isAir()) {level.setBlockAndUpdate(pos, before);return false;}
                level.setBlockAndUpdate(pos, updated);after = updated;
            }
        }
        for (var direction : net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING.getPossibleValues()) {
            var adjacent = pos.relative(direction);
            var adjacentState = level.getBlockState(adjacent);
            var updated = adjacentState.updateShape(direction.getOpposite(), after, level, adjacent, pos);
            if (updated != adjacentState) level.setBlockAndUpdate(adjacent, updated);
        }
        return true;
    }
    public static void giveAdvancement(ServerPlayer player, String namespace, String id, String criterion) {
        var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(namespace, id));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }
}
