package com.destan.trafficengine.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Retired support IDs load as the existing traffic-sign post on both loaders. */
public final class SignPostMigration {
    public static final String REPLACEMENT = "trafficengine:traffic_sign_post";
    private SignPostMigration() {}

    public static boolean isRemoved(String id) {
        return switch (id) {
            case "trafficengine:cantilever_post", "trafficengine:cantilever_pillar",
                    "trafficengine:cantilever_arm", "trafficengine:cantilever_head" -> true;
            default -> false;
        };
    }

    private static String legacyBlock(String id) {
        if (!id.startsWith("trafficcraft:")) return id;
        String replacement = "trafficengine:" + id.substring("trafficcraft:".length());
        var key = net.minecraft.resources.ResourceLocation.tryParse(replacement);
        return key != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(key) ? replacement : id;
    }

    public static void blockState(CompoundTag tag) {
        String original=tag.getString("Name"), id=legacyBlock(original);
        if (!id.equals(original)) tag.putString("Name", id);
        if (id.equals("trafficengine:road_reflector")) {
            tag.putString("Name", "trafficengine:reflector");
            return;
        }
        if (!isRemoved(id)) return;
        tag.putString("Name", REPLACEMENT);
        CompoundTag properties=tag.getCompound("Properties");
        if (!properties.contains("axis")) {
            String facing=properties.getString("facing");
            properties.putString("axis", id.endsWith("_arm")
                    ? (facing.equals("east") || facing.equals("west") ? "x" : "z") : "y");
        }
        properties.remove("facing");
        tag.put("Properties", properties);
    }

    public static void item(CompoundTag tag) {
        String original=tag.getString("id"), mapped=legacyBlock(original);
        if (!mapped.equals(original)) tag.putString("id", mapped);
        if (tag.getString("id").equals("trafficengine:road_reflector")) tag.putString("id", "trafficengine:reflector");
        else if (isRemoved(tag.getString("id"))) tag.putString("id", REPLACEMENT);
    }

    public static void chunk(CompoundTag tag) {
        var sections=tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i=0;i<sections.size();i++) {
            var palette=sections.getCompound(i).getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
            for (int j=0;j<palette.size();j++) blockState(palette.getCompound(j));
        }
    }
}
