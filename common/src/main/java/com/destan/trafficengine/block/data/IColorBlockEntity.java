package com.destan.trafficengine.block.data;

import com.destan.trafficengine.data.PaintColor;

public interface IColorBlockEntity {

    public static final String NBT_COLOR = "color";

    void setColor(PaintColor color);
    PaintColor getColor();
}
