package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.block.entity.WritableTrafficSignBlockEntity;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class WritableSignBlockEntityRenderer<T extends WritableTrafficSignBlockEntity> extends BaseBlockEntityRenderer<T> {
    protected final Font font;

    public WritableSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        font = context.getFont();
    }

    @Override
    public void renderSafe(RenderContext<T> graphics, float partialTick) {
        WritableSignConfig config = graphics.blockEntity().getRenderConfig();
        renderInternal(config, graphics, false, false);
        if (config.renderBack()) renderInternal(config, graphics, false, true);
    }

    protected void renderInternal(WritableSignConfig config, RenderContext<T> graphics, boolean backText, boolean opposite) {
        var entity = graphics.blockEntity();
        float unit = 1 / config.scale();
        float rotation = (float) config.blockEntityRendererRotation().applyAsDouble(entity.getBlockState()) + (opposite ? 180 : 0);
        for (int i = 0; i < config.lineData().length; i++) {
            String text = backText ? ((TownSignBlockEntity) entity).getBackText(i) : entity.getText(i);
            if (text.isEmpty()) continue;
            var lettering = WritableSignLettering.layout(font, text);
            int width = lettering.width();
            if (width == 0) continue;
            float scaleX = config.scaleX(i, width);
            float scaleY = config.scaleY(i, width);
            var pose = graphics.poseStack();
            pose.pushPose();
            pose.translate(.5, .5, .5);
            pose.mulPose(Axis.YP.rotationDegrees(rotation));
            pose.translate((opposite ? -1 : 1) * config.berX(), config.berY(), config.berZ());
            pose.scale(unit, -unit, unit);
            pose.translate(config.lineData()[i].xOffset() / unit, config.baseline(i, scaleY), 0);
            pose.scale(scaleX, scaleY, 1);
            font.drawInBatch(lettering.glyphs(), -width * .5F, 0, config.berColor() | 0xFF000000, false,
                pose.last().pose(), graphics.multiBufferSource(), Font.DisplayMode.NORMAL, 0, graphics.packedLight());
            pose.popPose();
        }
    }
}
