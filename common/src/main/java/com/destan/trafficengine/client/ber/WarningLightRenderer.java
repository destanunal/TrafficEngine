package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.WarningLightBlock;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.client.TrafficLightTextureManager;

public final class WarningLightRenderer {
    private WarningLightRenderer() {}
    public static void render(RenderContext<TrafficLightBlockEntity> graphics) {
        var lamp = graphics.blockEntity(); var state = lamp.getBlockState();
        int count = state.getValue(TrafficLightBlock.MODEL).getLightsCount() == 1 ? 1 : 2;
        boolean horizontal = state.getValue(WarningLightBlock.HORIZONTAL);
        int mask = lamp.isWarningActive() ? lamp.getFlashSequence().mask(lamp.getLevel().getGameTime()) : 0;
        if ((mask & ((1 << count) - 1)) == 0) return;
        graphics.poseStack().pushPose();
        graphics.poseStack().translate(count == 2 && horizontal ? 3 : 6, count == 2 && !horizontal ? 7 : 10, 8.6);
        for (int slot = 0; slot < count; slot++) {
            if ((mask & 1 << slot) != 0)
                TrafficLightTextureManager.renderWarningLens(graphics, lamp.getWarningColor(slot));
            graphics.poseStack().translate(horizontal ? 6 : 0, horizontal ? 0 : 6, 0);
        }
        graphics.poseStack().popPose();
    }
}
