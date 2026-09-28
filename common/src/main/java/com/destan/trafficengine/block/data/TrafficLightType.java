package com.destan.trafficengine.block.data;

import java.util.Arrays;

import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import com.destan.trafficengine.TrafficEngine;

public enum TrafficLightType implements ITranslatableEnum, IIconEnum {
	NOCOUNTDOWN("nocountdown", 0, 0, 0), // 0. Sütun (1. İkon - Sayaçsız)
	COUNTDOWN("countdown", 1, 1, 0); // 1. Sütun (2. İkon - Sayaçlı)

	private String name;
	private byte index;
	private int uMul;
	private int vMul;

	private TrafficLightType(String name, int index, int u, int v) {
		this.name = name;
		this.index = (byte)index;
		this.uMul = u;
		this.vMul = v;
	}

	public String getName() {
		return this.name;
	}

	public byte getIndex() {
		return this.index;
	}

	public String getTranslationKey() {
		return String.format("enum.trafficengine.trafficlighttype.%s", name);
	}

	@Override
	public int getUMultiplier() {
		return uMul;
	}

	@Override
	public int getVMultiplier() {
		return vMul;
	}

	public static TrafficLightType getTypeByIndex(byte index) {
		return Arrays.stream(TrafficLightType.values()).filter(x -> x.getIndex() == index).findFirst().orElse(TrafficLightType.NOCOUNTDOWN);
	}

	@Override
	public Data getTranslationData() {
		return new Data(TrafficEngine.MOD_ID, "trafficlighttype", name);
	}
}