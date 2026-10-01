package com.destan.trafficengine.data;
import net.minecraft.nbt.CompoundTag;
public interface NbtSerializable {CompoundTag serializeNbt();void deserializeNbt(CompoundTag tag);}
