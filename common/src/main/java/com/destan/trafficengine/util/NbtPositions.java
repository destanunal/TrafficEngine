package com.destan.trafficengine.util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
public final class NbtPositions {
    private NbtPositions() {}
    public static void putNbtPos(CompoundTag tag, String name, BlockPos pos) {
        CompoundTag data = new CompoundTag();
        data.putInt("X", pos.getX()); data.putInt("Y", pos.getY()); data.putInt("Z", pos.getZ()); tag.put(name, data);
    }
    public static BlockPos getNbtBlockPos(CompoundTag tag, String name) {
        CompoundTag data = tag.getCompound(name); return new BlockPos(data.getInt("X"), data.getInt("Y"), data.getInt("Z"));
    }
}
