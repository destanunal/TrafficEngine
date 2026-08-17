package com.destan.trafficengine.world;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.config.ModCommonConfig;
import dev.architectury.registry.level.biome.BiomeModifications;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;

public class ModWorldGen {

    public static void init() {
        BiomeModifications.addProperties((ctx, mutable) -> {
            // HACKER BİLGİSİ: Çökmeye sebep olan Bitüm Madeni (bitumen_ore) üretim kodu buradan tamamen imha edildi!

            // Sadece okyanus tabanlarında üretilen Tuz (Salt) madeni aktif bırakıldı.
            if (ModCommonConfig.SALT_GENERATION.get() && ctx.hasTag(BiomeTags.IS_OCEAN)) {
                mutable.getGenerationProperties().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, PlacementUtils.createKey(TrafficEngine.MOD_ID + ":disk_salt_placed"));
            }
        });
    }
}