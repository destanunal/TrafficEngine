package com.destan.trafficengine.registry;

import com.destan.trafficengine.util.ModUtils;
import com.destan.trafficengine.TrafficEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModBlockTags {

    public static final TagKey<Block> POST_EXTENSION = TagKey.create(Registries.BLOCK, ModUtils.resourceLocation(TrafficEngine.MOD_ID, "requires_post_extension"));

    /** Keep 60% of vanilla break particles for performance-sensitive road features. */
    public static final TagKey<Block> REDUCED_BREAK_PARTICLES = TagKey.create(Registries.BLOCK, ModUtils.resourceLocation(TrafficEngine.MOD_ID, "reduced_break_particles"));

    public static void init() {
    }
}
