package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;

public class TownSignScreen extends WritableTrafficSignScreen {
    public TownSignScreen(TownSignBlockEntity sign, TownSignBlock.ETownSignSide side) { super(sign, side); }
}
