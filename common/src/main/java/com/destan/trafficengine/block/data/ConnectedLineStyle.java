package com.destan.trafficengine.block.data;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum ConnectedLineStyle implements StringRepresentable {
    SOLID, DASHED, DOUBLE, WIDE;
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public static ConnectedLineStyle byIndex(int index) {
        return values()[Math.max(0, Math.min(values().length - 1, index))];
    }
}
