package com.destan.trafficengine.client.ber;

import java.util.function.Function;

import org.joml.Vector3f;

import com.mojang.math.Axis;

import com.destan.trafficengine.block.entity.WritableTrafficSignBlockEntity;
import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.client.ber.BaseBlockEntityRenderer;
import com.destan.trafficengine.data.SignTextConfig;
import com.destan.trafficengine.data.SignTextConfig.ConfiguredLineData;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.client.ber.SignRenderUtils;
import com.destan.trafficengine.util.ColorValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class WritableSignBlockEntityRenderer<T extends WritableTrafficSignBlockEntity> extends BaseBlockEntityRenderer<T> {
    protected final Font font;

    public WritableSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.font = context.getFont();
    }

    @Override
    public void renderSafe(RenderContext<T> graphics, float pPartialTick) {
        WritableSignConfig config = graphics.blockEntity().getRenderConfig();        
        renderInternal(config, graphics.blockEntity()::getText, pPartialTick, graphics, false);
        if (config.renderBack()) {
            renderInternal(config, graphics.blockEntity()::getText, pPartialTick, graphics, true);
        }
    }

    protected void renderInternal(WritableSignConfig config, Function<Integer, String> getText, float pPartialTick, RenderContext<T> graphics, boolean isOpposite) {
        BlockState blockState = graphics.blockEntity().getBlockState();
        final float scale = 1.0F / config.scale();

        for (int lineIndex = 0; lineIndex < config.lineData().length; ++lineIndex) {
            String line = getText.apply(lineIndex);
            if (line == null || this.font.width(line) == 0)
                continue;
                
            ConfiguredLineData data = config.lineData()[lineIndex];
            graphics.poseStack().pushPose();
            graphics.poseStack().translate(0.5D, 0.5f, 0.5F);
            graphics.poseStack().mulPose(Axis.YP.rotationDegrees(config.blockEntityRendererRotation().apply(blockState) + (isOpposite ? 180 : 0)));
            graphics.poseStack().translate((isOpposite ? -1 : 1) * config.berX(), config.berY(), config.berZ());
            float xCenter = (float)(-this.font.width(line) / 2);

            Vector3f vector3f = config.berTextScale(line, font.width(line), scale, data);
            graphics.poseStack().scale(scale, -scale, scale);
            graphics.poseStack().translate(data.xOffset() / scale, data.yOffset() / scale - (SignTextConfig.DEFAULT_LINE_HEIGHT / 2 * config.lineData()[0].lineHeightScale()) + config.getLineHeightsUntil(lineIndex) + config.getLineOffset(lineIndex, vector3f.y()), 0);
            graphics.poseStack().scale(vector3f.x(), vector3f.y(), vector3f.z());
            SignRenderUtils.drawString(graphics, font, xCenter, 0, line, ColorValue.fromInt(config.berColor()), false);
                        
            graphics.poseStack().popPose();
        }
    }
}
