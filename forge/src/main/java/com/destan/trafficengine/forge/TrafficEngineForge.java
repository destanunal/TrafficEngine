package com.destan.trafficengine.forge;

import dev.architectury.platform.forge.EventBuses;
import com.destan.trafficengine.TrafficEngine;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// YENİ EKLENEN İMPORTLAR
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

@Mod(TrafficEngine.MOD_ID)
public class TrafficEngineForge {

    public TrafficEngineForge() {
        EventBuses.registerModEventBus(TrafficEngine.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        TrafficEngine.init();

        // Forge sistemine dönüşüm kodumuzu dinlemesini söylüyoruz:
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMissingMappings(MissingMappingsEvent event) {

        // 1. BLOKLARI DÖNÜŞTÜR (Yerdeki asfaltlar, lambalar)
        for (MissingMappingsEvent.Mapping<Block> mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, "trafficcraft")) {
            Block newBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("trafficengine", mapping.getKey().getPath()));
            if (newBlock != null) mapping.remap(newBlock);
        }

        // 2. EŞYALARI DÖNÜŞTÜR (Envanterdeki fırçalar, kataloglar)
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, "trafficcraft")) {
            Item newItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("trafficengine", mapping.getKey().getPath()));
            if (newItem != null) mapping.remap(newItem);
        }

        // 3. HAFIZALARI DÖNÜŞTÜR (Lambaların süreleri, tabelaların desenleri)
        for (MissingMappingsEvent.Mapping<BlockEntityType<?>> mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, "trafficcraft")) {
            BlockEntityType<?> newBe = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(new ResourceLocation("trafficengine", mapping.getKey().getPath()));
            if (newBe != null) mapping.remap(newBe);
        }
    }
}