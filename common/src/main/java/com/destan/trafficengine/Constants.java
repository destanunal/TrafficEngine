package com.destan.trafficengine;

import java.util.Random;

import com.destan.trafficengine.util.ColorValue;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

public class Constants {
    public static final int MAX_ASPHALT_PATTERNS = 318;
    public static final int MAX_PAINT = 128;

    public static final ColorValue METAL_COLOR = ColorValue.fromInt(0xFF828282);
    public static final ColorValue TRAFFIC_CONE_BASE_COLOR = ColorValue.fromInt(0xFFD12725);

    public static final MutableComponent CREATIVE_MODE_ONLY_TOOLTIP = Component.translatable("core.trafficengine.creative_only.tooltip").withStyle(ChatFormatting.GOLD);
    
    public static final Component textCopy = Component.translatable("core.trafficengine.common.copy");
    public static final Component textPaste = Component.translatable("core.trafficengine.common.paste");

    public static final Random RANDOM = new Random();
    public static final RandomSource RANDOM_SOURCE = RandomSource.create();
}


