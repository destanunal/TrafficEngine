package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class TownSignBlockEntityRenderer extends WritableSignBlockEntityRenderer<TownSignBlockEntity> {
    
    public TownSignBlockEntityRenderer(Context context) {
        super(context);
    }

    @Override
    public void renderSafe(RenderContext<TownSignBlockEntity> graphics, float pPartialTick) {
        switch (graphics.blockEntity().getBlockState().getValue(TownSignBlock.VARIANT)) {
            case FRONT:
                renderInternal(graphics.blockEntity().getRenderConfig(), graphics, false, false);
                break;
            case BACK:
                renderInternal(graphics.blockEntity().getBackRenderConfig(), graphics, true, false);
                break;
            case BOTH:
                renderInternal(graphics.blockEntity().getRenderConfig(), graphics, false, false);
                renderInternal(graphics.blockEntity().getBackRenderConfig(), graphics, true, false);
                break;
        }
    }
}
