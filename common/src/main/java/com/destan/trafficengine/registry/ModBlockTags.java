package com.destan.trafficengine.registry;

import de.mrjulsen.mcdragonlib.util.DLUtils;
import com.destan.trafficengine.TrafficEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModBlockTags {

    public static final TagKey<Block> POST_EXTENSION = TagKey.create(Registries.BLOCK, DLUtils.resourceLocation(TrafficEngine.MOD_ID, "requires_post_extension"));

    public static void init() {
    }
}
