package com.destan.trafficengine.registry;

import de.mrjulsen.mcdragonlib.util.TextUtils;
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
    
    public static final RegistrySupplier<CreativeModeTab> MOD_TAB = TABS.register(new ResourceLocation(TrafficEngine.MOD_ID, "trafficenginetab"),
            () -> CreativeTabRegistry.create(
                    TextUtils.translate("itemGroup.trafficengine.trafficenginetab"),
                    () -> new ItemStack(ModBlocks.TRAFFIC_LIGHT.get())
            )
    );

    public static void init() {
        TABS.register();
    } 

}
