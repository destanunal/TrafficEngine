package com.destan.trafficengine.block.data;

import com.destan.trafficengine.registry.ModBlocks;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

public enum RoadType implements StringRepresentable {
    NONE("none", 0, 0xFFFFFFFF),
    ASPHALT("asphalt", 1, 0xFF373432),
    CONCRETE("concrete", 2, 0xFFB9B3A7),
    CRACKED_ASPHALT("cracked_asphalt", 4, 0xFF373432),
    HEAVY_CRACKED_ASPHALT("heavy_cracked_asphalt", 5, 0xFF373432),
    LIGHT_ASPHALT("light_asphalt", 6, 0xFF656565),
    DARK_ASPHALT("dark_asphalt", 7, 0xFF222222),
    DIRTY_ASPHALT("dirty_asphalt", 8, 0xFF4A4235);

    private String roadType;
    private int index;
    private int color;

    private RoadType(String roadType, int index, int color) {
        this.roadType = roadType;
        this.index = index;
        this.color = color;
    }

    public String getRoadType() {
        return this.roadType;
    }

    public int getIndex() {
        return this.index;
    }

    public int getColor() {
        return this.color;
    }

    public String getTranslationKey() {
        return String.format("gui.trafficengine.road.roadtype.%s", roadType);
    }

    public static RoadType getRoadTypeByIndex(int index) {
        for (RoadType controlType : RoadType.values()) {
            if (controlType.getIndex() == index) {
                return controlType;
            }
        }
        return RoadType.NONE;
    }

    @Override
    public String getSerializedName() {
        return this.roadType;
    }

    public Block getBlock() {
        switch (this) {
            default:
            case ASPHALT: return ModBlocks.ASPHALT.get();
            case CONCRETE: return ModBlocks.CONCRETE.get();
            case CRACKED_ASPHALT: return ModBlocks.CRACKED_ASPHALT.get();
            case HEAVY_CRACKED_ASPHALT: return ModBlocks.HEAVY_CRACKED_ASPHALT.get();
            case LIGHT_ASPHALT: return ModBlocks.LIGHT_ASPHALT.get();
            case DARK_ASPHALT: return ModBlocks.DARK_ASPHALT.get();
            case DIRTY_ASPHALT: return ModBlocks.DIRTY_ASPHALT.get();
        }
    }

    public Block getSlope() {
        switch (this) {
            default:
            case ASPHALT:
            case CRACKED_ASPHALT:
            case HEAVY_CRACKED_ASPHALT:
            case LIGHT_ASPHALT:
            case DARK_ASPHALT:
            case DIRTY_ASPHALT:
                return ModBlocks.ASPHALT_SLOPE.get(); // Şimdilik yeni asfaltların rampaları normal asfalt rampası olacak
            case CONCRETE:
                return ModBlocks.CONCRETE_SLOPE.get();
        }
    }
}