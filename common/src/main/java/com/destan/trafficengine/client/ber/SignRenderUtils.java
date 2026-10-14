package com.destan.trafficengine.client.ber;

import java.util.BitSet;
import org.joml.Vector3f;
import com.destan.trafficengine.util.ColorValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class SignRenderUtils {
    private static final ModelBlockRenderer.AmbientOcclusionFace AO = new ModelBlockRenderer.AmbientOcclusionFace();
    private static final float[] BOUNDS = new float[12];
    private static final BitSet FLAGS = new BitSet(3);
    private SignRenderUtils() {}

    public static void drawString(RenderContext<?> graphics, Font font, float x, float y, String text, ColorValue color, boolean shadow) {
        font.drawInBatch(text, x, y, color.getAsARGB(), shadow, graphics.poseStack().last().pose(), graphics.multiBufferSource(), Font.DisplayMode.NORMAL, 0, graphics.packedLight());
    }

    public static void renderTexture(ResourceLocation texture, RenderContext<?> graphics, Vector3f pos, float w, float h, float u, float v, float uW, float vH, Direction facing, ColorValue tint, int light, boolean ambientOcclusion) {
        var consumer = graphics.multiBufferSource().getBuffer(RenderType.text(texture));
        boolean ao = ambientOcclusion && Minecraft.useAmbientOcclusion() && graphics.blockEntity().getLevel() != null;
        if (ao) AO.calculate(graphics.blockEntity().getLevel(), graphics.blockEntity().getBlockState(), graphics.blockEntity().getBlockPos(), facing, BOUNDS, FLAGS, true);
        vertex(consumer, graphics, pos.x, pos.y, pos.z, u, v, tint, ao ? AO.brightness[0] : 1, ao ? AO.lightmap[0] : light);
        vertex(consumer, graphics, pos.x, pos.y + h, pos.z, u, v + vH, tint, ao ? AO.brightness[1] : 1, ao ? AO.lightmap[1] : light);
        vertex(consumer, graphics, pos.x + w, pos.y + h, pos.z, u + uW, v + vH, tint, ao ? AO.brightness[2] : 1, ao ? AO.lightmap[2] : light);
        vertex(consumer, graphics, pos.x + w, pos.y, pos.z, u + uW, v, tint, ao ? AO.brightness[3] : 1, ao ? AO.lightmap[3] : light);
    }

    private static void vertex(VertexConsumer consumer, RenderContext<?> graphics, float x, float y, float z, float u, float v, ColorValue color, float brightness, int light) {
        var pose = graphics.poseStack().last();
        consumer.vertex(pose.pose(), x, y, z).color(color.getRed() / 255F * brightness, color.getGreen() / 255F * brightness, color.getBlue() / 255F * brightness, (color.argb() >>> 24) / 255F).uv(u,v).uv2(light).overlayCoords(OverlayTexture.NO_OVERLAY).normal(pose.normal(),0,0,1).endVertex();
    }
}
