package com.destan.trafficengine.util;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

public final class PortingInteractions {
    private PortingInteractions() {}

    public static ItemInteractionResult forItem(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
}
