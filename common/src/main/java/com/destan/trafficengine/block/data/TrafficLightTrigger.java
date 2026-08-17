package com.destan.trafficengine.block.data;

import java.util.Arrays;

import de.mrjulsen.mcdragonlib.data.IIterableEnum;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.registry.ModBlocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public enum TrafficLightTrigger implements ITranslatableEnum, IItemIcon, IIterableEnum<TrafficLightTrigger> {
    NONE("none", 0, Blocks.BARRIER),
	ON_REQUEST("on_request", 1, ModBlocks.TRAFFIC_LIGHT_REQUEST_BUTTON.get()),
	REDSTONE("redstone", 2, Items.REDSTONE);
	
	private String trigger;
	private byte index;
	private ItemLike icon;
	
	private TrafficLightTrigger(String shape, int index, ItemLike icon) {
		this.trigger = shape;
		this.index = (byte)index;
		this.icon = icon;
	}

	@Override
	public ItemLike getItemIcon() {
		return icon;
	}
	
	public String getTrigger() {
		return this.trigger;
	}

	public byte getIndex() {
		return this.index;
	}

	public String getTranslationKey() {
		return String.format("gui.trafficengine.trafficlight.trigger.%s", trigger);
	}

	public static TrafficLightTrigger getTriggerByIndex(byte index) {
		return Arrays.stream(TrafficLightTrigger.values()).filter(x -> x.getIndex() == index).findFirst().orElse(TrafficLightTrigger.NONE);
	}

    @Override
    public String getSerializedName() {
        return trigger;
    }

	@Override
	public Data getTranslationData() {
		return new Data(TrafficEngine.MOD_ID, "trafficlighttrigger", trigger);
	}

	@Override
	public TrafficLightTrigger[] getValues() {
		return values();
	}
}
