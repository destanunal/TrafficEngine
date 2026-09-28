package com.destan.trafficengine.client.ber;

import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class TownSignBlockEntityRenderer extends WritableSignBlockEntityRenderer<TownSignBlockEntity> {
    
    public TownSignBlockEntityRenderer(Context context) {
        super(context);
    }

    @Override
    public void renderSafe(BERGraphics<TownSignBlockEntity> graphics, float pPartialTick) {
        switch (graphics.blockEntity().getBlockState().getValue(TownSignBlock.VARIANT)) {
            case FRONT:
                renderInternal(graphics.blockEntity().getRenderConfig(), graphics.blockEntity()::getText, pPartialTick, graphics, false);
                break;
            case BACK:
                renderInternal(graphics.blockEntity().getBackRenderConfig(), graphics.blockEntity()::getBackText, pPartialTick, graphics, false);
                break;
            case BOTH:
                renderInternal(graphics.blockEntity().getRenderConfig(), graphics.blockEntity()::getText, pPartialTick, graphics, false);
                renderInternal(graphics.blockEntity().getBackRenderConfig(), graphics.blockEntity()::getBackText, pPartialTick, graphics, false);
                break;
        }
    }
}
