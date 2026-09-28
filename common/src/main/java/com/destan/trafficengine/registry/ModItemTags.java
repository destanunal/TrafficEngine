package com.destan.trafficengine.registry;

import dev.architectury.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModItemTags {

    public static final TagKey<Item> WRENCHES = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench"));

    public static void init() {
    }
}
