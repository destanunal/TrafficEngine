package com.destan.trafficengine.client.ber;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Keeps sign artwork in front of the casing at distant depth-buffer precision. */
final class RoadSignRenderTypes extends RenderType {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("trafficengine", "textures/block/road_sign_white.png");
    private static final RenderType BACKGROUND = layer("road_sign_background", -4);
    private static final RenderType FOREGROUND = layer("road_sign_foreground", -7);
    private static final RenderType LED = layer("road_sign_led", -10);

    private RoadSignRenderTypes() {
        super("road_sign", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
    }

    static RenderType atDepth(float depth) {
        return depth < 0.2F ? BACKGROUND : depth < 0.3F ? FOREGROUND : LED;
    }

    private static final java.util.Map<RenderType, RenderType> LETTERING = new java.util.IdentityHashMap<>();

    static RenderType lettering(RenderType original) {
        return LETTERING.computeIfAbsent(original, LetteringLayer::new);
    }

    private static final class LetteringLayer extends RenderType {
        LetteringLayer(RenderType original) {
            super("road_sign_lettering", original.format(), original.mode(), original.bufferSize(),
                    original.affectsCrumbling(), true, () -> {
                        original.setupRenderState();
                        // Bold glyphs overlap. Test against the sign, without writing over each other.
                        RenderSystem.depthMask(false);
                        RenderSystem.polygonOffset(0, -14);
                        RenderSystem.enablePolygonOffset();
                    }, () -> {
                        original.clearRenderState();
                        RenderSystem.depthMask(true);
                        RenderSystem.polygonOffset(0, 0);
                        RenderSystem.disablePolygonOffset();
                    });
        }
    }

    private static RenderType layer(String name, float units) {
        LayeringStateShard bias = new LayeringStateShard(name, () -> {
            RenderSystem.polygonOffset(0, units);
            RenderSystem.enablePolygonOffset();
        }, () -> {
            RenderSystem.polygonOffset(0, 0);
            RenderSystem.disablePolygonOffset();
        });
        // Lettering uses -14 units, ahead of these background, panel and LED layers.
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                256, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_TEXT_SHADER)
                        .setTextureState(new TextureStateShard(WHITE, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setLayeringState(bias)
                        .createCompositeState(false));
    }
}
