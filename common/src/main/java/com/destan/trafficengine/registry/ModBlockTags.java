package com.destan.trafficengine.registry;

import com.destan.trafficengine.util.ModUtils;
import com.destan.trafficengine.TrafficEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModBlockTags {

    public static final TagKey<Block> POST_EXTENSION = TagKey.create(Registries.BLOCK, ModUtils.resourceLocation(TrafficEngine.MOD_ID, "requires_post_extension"));

    public static void init() {
    }
}
