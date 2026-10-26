package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.block.WritableTrafficSign;
import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class RoadInformationSignRenderer extends BaseBlockEntityRenderer<RoadInformationSignBlockEntity> {
    // A fixed physical clearance keeps the thin border ahead of the baked casing
    // at shallow angles; cached render layers provide the remaining depth bias.
    private static final double ARTWORK_CLEARANCE = 1.0 / 256;
    private final Font font;
    private final RoadSignGraphics.Layout layout = new RoadSignGraphics.Layout();
    public RoadInformationSignRenderer(BlockEntityRendererProvider.Context context) { super(context); font = context.getFont(); }
    @Override protected void renderSafe(RenderContext<RoadInformationSignBlockEntity> graphics, float tick) {
        var sign = graphics.blockEntity();
        Kind kind = sign.kind();
        boolean mounted=sign.getBlockState().getValue(RoadInformationSignBlock.POST_MOUNTED);
        boolean wallMounted=sign.getBlockState().getValue(RoadInformationSignBlock.WALL_MOUNTED);
        float rotation = switch (sign.getBlockState().getValue(WritableTrafficSign.FACING)) {
            case EAST -> 90; case SOUTH -> 0; case WEST -> -90; default -> 180;
        };
        var pose = graphics.poseStack(); pose.pushPose();
        pose.translate(0.5, RoadInformationSignBlock.topHeight(kind,mounted)/16, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(0, 0, 0.5-RoadInformationSignBlock.frontDepth(mounted,wallMounted)/16+ARTWORK_CLEARANCE);
        pose.scale(1F/96, -1F/96, 1F/96); pose.translate(-RoadSignGraphics.width(kind)/2F, 0, 0);
        new RoadSignGraphics(pose, graphics.multiBufferSource(), font,
            kind == Kind.VARIABLE_SPEED ? LightTexture.FULL_BRIGHT : graphics.packedLight(), true, layout).draw(kind,
                sign.destinations(),
                sign.arrows(),
                sign.distances(),sign.route(),sign.theme(),sign.displayedSpeed(),sign.enabled(),sign.design(),sign.horizontalConnections());
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(RoadInformationSignBlockEntity sign) {
        return sign.kind()!=Kind.VARIABLE_SPEED || sign.getBlockState().getValue(RoadInformationSignBlock.POST_MOUNTED);
    }
}
