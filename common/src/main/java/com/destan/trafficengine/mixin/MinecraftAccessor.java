package com.destan.trafficengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {    
    @Accessor("itemColors")
    ItemColors trafficengine$getItemColors();
}
