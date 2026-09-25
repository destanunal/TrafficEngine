package com.destan.trafficengine.data;

import com.destan.trafficengine.block.data.TrafficSignShape;
import net.minecraft.resources.ResourceLocation;

public record TrafficSignTextureMetadata(ResourceLocation location, TrafficSignShape shape, int id, short width, short height) {}
