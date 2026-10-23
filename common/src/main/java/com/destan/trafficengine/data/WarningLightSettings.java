package com.destan.trafficengine.data;

import net.minecraft.nbt.CompoundTag;

/** Warning-only data; housing orientation and lamp count belong to the block state. */
public record WarningLightSettings(int primary, int secondary, FlashSequence sequence, boolean redstone) {
    public static final FlashSequence DEFAULT_SEQUENCE = new FlashSequence("10", 40);
    public WarningLightSettings {
        primary |= 0xFF000000; secondary |= 0xFF000000;
        if (sequence == null) sequence = DEFAULT_SEQUENCE;
    }
    public static WarningLightSettings read(CompoundTag tag) {
        return new WarningLightSettings(tag.contains("warning_primary") ? tag.getInt("warning_primary") : 0xFFFFAA00,
            tag.contains("warning_secondary") ? tag.getInt("warning_secondary") : 0xFFFFAA00,
            tag.contains("flash_steps") ? new FlashSequence(tag.getString("flash_steps"), tag.getInt("flash_interval")) : DEFAULT_SEQUENCE,
            tag.getBoolean("warning_redstone"));
    }
    public void write(CompoundTag tag) {
        tag.putInt("warning_primary", primary); tag.putInt("warning_secondary", secondary);
        tag.putString("flash_steps", sequence.steps()); tag.putInt("flash_interval", sequence.stepTicks());
        tag.putBoolean("warning_redstone", redstone);
    }
}
