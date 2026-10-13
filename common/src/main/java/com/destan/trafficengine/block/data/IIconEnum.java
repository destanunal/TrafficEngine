package com.destan.trafficengine.block.data;

import com.destan.trafficengine.client.gui.GuiIcon;
import net.minecraft.resources.ResourceLocation;
import com.destan.trafficengine.util.ModUtils;
import com.destan.trafficengine.TrafficEngine;

public interface IIconEnum {

	public static final ResourceLocation ICON_TEXTURE = new ResourceLocation(TrafficEngine.MOD_ID, "textures/gui/icons.png");
	public static final int DEFAULT_SPRITE_SIZE = 16;

    int getUMultiplier();
    int getVMultiplier();

    default GuiIcon getSprite() {
        return new GuiIcon(ICON_TEXTURE, DEFAULT_SPRITE_SIZE * getUMultiplier(), DEFAULT_SPRITE_SIZE * getVMultiplier(), DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE, 256, 256);
    }
}
