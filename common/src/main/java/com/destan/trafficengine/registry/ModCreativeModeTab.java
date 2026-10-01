package com.destan.trafficengine.registry;

import net.minecraft.network.chat.Component;
import com.destan.trafficengine.TrafficEngine;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeModeTab {


    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(TrafficEngine.MOD_ID, Registries.CREATIVE_MODE_TAB);
    
    public static final RegistrySupplier<CreativeModeTab> MOD_TAB = TABS.register(ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "trafficenginetab"),
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.trafficengine.trafficenginetab"))
                    .icon(() -> new ItemStack(ModBlocks.TRAFFIC_LIGHT.get()))
                    .displayItems(ModCreativeModeTab::displayItems))
    );

    /** Explicit tab contents keep the same order on Forge and Fabric. */
    private static void displayItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(ModItems.WRENCH.get());
        output.accept(ModItems.TRAFFIC_LIGHT_LINKER.get());
        output.accept(ModItems.NETHERITE_ROAD_CONSTRUCTION_TOOL.get());
        output.accept(ModItems.HAMMER.get());
        output.accept(ModItems.CREATIVE_PATTERN_CATALOGUE.get());
        output.accept(ModItems.PAINT_BRUSH.get());
        output.accept(ModBlocks.PAINT_BUCKET.get());
        output.accept(ModItems.STREET_LAMP_CONFIG_CARD.get());
        output.accept(ModBlocks.CONCRETE.get());
        output.accept(ModBlocks.ASPHALT.get());
        output.accept(ModBlocks.CRACKED_ASPHALT.get());
        output.accept(ModBlocks.HEAVY_CRACKED_ASPHALT.get());
        output.accept(ModBlocks.LIGHT_ASPHALT.get());
        output.accept(ModBlocks.DARK_ASPHALT.get());
        output.accept(ModBlocks.DIRTY_ASPHALT.get());
        output.accept(ModBlocks.ASPHALT_SLOPE.get());
        output.accept(ModBlocks.CRACKED_ASPHALT_SLOPE.get());
        output.accept(ModBlocks.HEAVY_CRACKED_ASPHALT_SLOPE.get());
        output.accept(ModBlocks.LIGHT_ASPHALT_SLOPE.get());
        output.accept(ModBlocks.DARK_ASPHALT_SLOPE.get());
        output.accept(ModBlocks.DIRTY_ASPHALT_SLOPE.get());
        output.accept(ModBlocks.TRAFFIC_LIGHT.get());
        output.accept(ModBlocks.HORIZONTAL_TRAFFIC_LIGHT.get());
        output.accept(ModBlocks.TRAFFIC_LIGHT_REQUEST_BUTTON.get());
        output.accept(ModBlocks.TRAFFIC_SIGN_POST.get());
        output.accept(ModBlocks.TRAFFIC_SIGN.get());
        output.accept(ModBlocks.DOUBLE_TRAFFIC_SIGN.get());
        output.accept(ModBlocks.TRAFFIC_SIGN_WORKBENCH.get());
        output.accept(ModBlocks.TRAFFIC_LIGHT_CONTROLLER.get());
        output.accept(ModBlocks.ASPHALT_CURB.get());
        output.accept(ModBlocks.CONCRETE_CURB.get());
        output.accept(ModBlocks.SIDEWALK.get());
        output.accept(ModBlocks.CONCRETE_SLOPE.get());
        output.accept(ModBlocks.ASPHALT_CURB_SLOPE.get());
        output.accept(ModBlocks.CONCRETE_CURB_SLOPE.get());
        output.accept(ModBlocks.SIDEWALK_SLOPE.get());
        output.accept(ModBlocks.CONCRETE_BARRIER.get());
        output.accept(ModBlocks.GUARDRAIL.get());
        output.accept(ModBlocks.RETRACTABLE_BARRIER.get());
        output.accept(ModBlocks.TRAFFIC_CONE.get());
        output.accept(ModBlocks.TRAFFIC_BOLLARD.get());
        output.accept(ModBlocks.TRAFFIC_BARREL.get());
        output.accept(ModBlocks.ROAD_BARRIER_FENCE.get());
        output.accept(ModBlocks.SPEED_BUMP.get());
        output.accept(ModBlocks.WIDE_SPEED_BUMP.get());
        output.accept(ModBlocks.BIKE_LANE_SEPARATOR.get());
        output.accept(ModBlocks.TOWN_SIGN.get());
        output.accept(ModBlocks.STREET_SIGN.get());
        output.accept(ModBlocks.HOUSE_NUMBER_SIGN.get());
        output.accept(ModBlocks.STREET_LAMP.get());
        output.accept(ModBlocks.DOUBLE_STREET_LAMP.get());
        output.accept(ModBlocks.SMALL_STREET_LAMP.get());
        output.accept(ModBlocks.SMALL_DOUBLE_STREET_LAMP.get());
        output.accept(ModBlocks.STREET_LIGHT.get());
        output.accept(ModBlocks.FLUORESCENT_TUBE_LAMP.get());
        output.accept(ModBlocks.LED_LIGHT.get());
        output.accept(ModBlocks.TRAFFIC_DISPLAY.get());
        output.accept(ModBlocks.LARGE_TRAFFIC_DISPLAY.get());
        output.accept(ModBlocks.MANHOLE.get());
        output.accept(ModBlocks.MANHOLE_COVER.get());
        output.accept(ModBlocks.ROAD_GULLY.get());
        output.accept(ModBlocks.WHITE_DELINEATOR.get());
        output.accept(ModBlocks.YELLOW_DELINEATOR.get());
        output.accept(ModBlocks.RED_DELINEATOR.get());
        output.accept(ModBlocks.SMALL_WHITE_DELINEATOR.get());
        output.accept(ModBlocks.SMALL_YELLOW_DELINEATOR.get());
        output.accept(ModBlocks.ROAD_SALT.get());
        output.accept(ModBlocks.REFLECTOR.get());
    }

    public static void init() {
        TABS.register();
    } 

}
