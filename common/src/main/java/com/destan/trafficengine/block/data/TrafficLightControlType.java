package com.destan.trafficengine.block.data;

import java.util.Arrays;

import de.mrjulsen.mcdragonlib.data.IIterableEnum;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItems;
import net.minecraft.world.level.ItemLike;

public enum TrafficLightControlType implements ITranslatableEnum, IItemIcon, IIterableEnum<TrafficLightControlType> {
    STATIC("static", 0, ModBlocks.TRAFFIC_LIGHT.get()),
	OWN_SCHEDULE("own_schedule", 1, ModItems.CREATIVE_PATTERN_CATALOGUE.get()),
	REMOTE("remote", 2, ModBlocks.TRAFFIC_LIGHT_CONTROLLER.get());
	
	private String controlType;
	private byte index;
	private ItemLike icon;
	
	private TrafficLightControlType(String controlType, int index, ItemLike icon) {
		this.controlType = controlType;
		this.index = (byte)index;
		this.icon = icon;
	}
	
	public String getControlType() {
		return this.controlType;
	}

	public byte getIndex() {
		return this.index;
	}

	@Override
	public ItemLike getItemIcon() {
		return icon;
	}

	public String getValueShortTranslationKey() {
		return String.format("enum.trafficengine.trafficlightcontroltype.short.%s", controlType);
	}

	public static TrafficLightControlType getControlTypeByIndex(byte index) {
		return Arrays.stream(TrafficLightControlType.values()).filter(x -> x.getIndex() == index).findFirst().orElse(TrafficLightControlType.STATIC);
	}

    @Override
    public String getSerializedName() {
        return controlType;
    }

	@Override
	public Data getTranslationData() {
		return new Data(TrafficEngine.MOD_ID, "trafficlightcontroltype", controlType);
	}

	@Override
	public TrafficLightControlType[] getValues() {
		return values();
	}
}
