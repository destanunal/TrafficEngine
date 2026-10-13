package com.destan.trafficengine.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TrafficLightTextureManager {
    private static final TrafficLightBulbModel FALLBACK_MODEL = TrafficLightBulbModel.create(null);
    private static final String TEXTURE_PATH = "block/traffic_light";
    private static final ResourceLocation OFF_TEXTURE = new ResourceLocation(TrafficEngine.MOD_ID, "textures/" + TEXTURE_PATH + "/off.png");
    private static final List<TrafficLightBulbModel> models = new ArrayList<>();
    private static final TrafficLightBulbModel[][] modelsByIconAndColor =
        new TrafficLightBulbModel[TrafficLightIcon.values().length][TrafficLightColor.values().length];

    static {
        Arrays.stream(TrafficLightIcon.values())
            .forEach(
                x -> Arrays.stream(TrafficLightColor.values())
                    .filter(y -> x.isApplicableToColor(y))
                    .forEach(y -> {
                        TrafficLightTextureKey key = new TrafficLightTextureKey(x, y);
                        TrafficLightBulbModel model = TrafficLightBulbModel.create(key);
                        models.add(model);
                        modelsByIconAndColor[key.getIcon().ordinal()][key.getColor().ordinal()] = model;
                    }));
    }


    public static ResourceLocation getResourceLocation(TrafficLightIcon icon, TrafficLightColor color) {
        return getResourceLocation(new TrafficLightTextureKey(icon, color));
    }

    public static ResourceLocation getResourceLocation(TrafficLightTextureKey key) {
        if (key.isOffState()) {
            return new ResourceLocation(TrafficEngine.MOD_ID, String.format("textures/%s/off.png", TEXTURE_PATH));
        }
        return new ResourceLocation(TrafficEngine.MOD_ID, String.format("textures/%s/%s_%s.png",
            TEXTURE_PATH,
            (key.getIcon().isApplicableToColor(key.getColor()) ? key.getIcon() : TrafficLightIcon.NONE).getName(),
            key.getColor().getName()
        ));
    }

    public static Collection<ResourceLocation> getAllTextureLocations() {
        return models.stream().map(x -> x.getKey().getTextureLocation()).toList();
    }

    public static void render(RenderContext<?> graphics, BlockEntity be, TrafficLightIcon icon, TrafficLightColor color, int packedLight) {
        TrafficLightIcon applicable = icon.isApplicableToColor(color) ? icon : TrafficLightIcon.NONE;
        TrafficLightBulbModel model = modelsByIconAndColor[applicable.ordinal()][color.ordinal()];
        (model == null ? FALLBACK_MODEL : model).render(graphics, be, packedLight);
    }

    public static void render(RenderContext<?> graphics, BlockEntity be, TrafficLightTextureKey key, int packedLight) {
        TrafficLightBulbModel model = modelsByIconAndColor[key.getIcon().ordinal()][key.getColor().ordinal()];
        (model == null ? FALLBACK_MODEL : model).render(graphics, be, packedLight);
    }

    public static class TrafficLightTextureKey {
        private final TrafficLightIcon icon;
        private final TrafficLightColor color;

        public TrafficLightTextureKey(TrafficLightIcon icon, TrafficLightColor color) {
            this.color = color;
            this.icon = icon.isApplicableToColor(color) ? icon : TrafficLightIcon.NONE;
        }

        public TrafficLightIcon getIcon() {
            return icon;
        }

        public TrafficLightColor getColor() {
            return color;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TrafficLightTextureKey other) {
                return getColor() == other.getColor() && getIcon() == other.getIcon();
            }
            return false;
        }

        public ResourceLocation getTextureLocation() {
            return TrafficLightTextureManager.getResourceLocation(getIcon(), getColor());
        }

        public void render(RenderContext<?> graphics, BlockEntity be, int packedLight) {
            TrafficLightTextureManager.render(graphics, be, this, packedLight);
        }

        public boolean isOffState() {
            return getIcon() == TrafficLightIcon.NONE && getColor() == TrafficLightColor.NONE;
        }
    }

    private static final class TrafficLightBulbModel {
        private static final float[][] POSITIONS = {
            {0,-4,1, 0,0,1, 4,0,1, 4,-4,1},
            {4,-4,1, 4,0,1, 4,0,0, 4,-4,0},
            {0,-4,0, 0,0,0, 0,0,1, 0,-4,1},
            {0,0,1, 0,0,0, 4,0,0, 4,0,1}
        };
        private static final float[][] NORMALS = {{0,0,1},{1,0,0},{-1,0,0},{0,-1,0}};
        private static final float[] UV = {0,0, 0,1, 1,1, 1,0};
        private final TrafficLightTextureKey key;
        private final RenderType renderType;
        private final ResourceLocation iconTexture;
        private final boolean separateIcon;
        private boolean textureRegistered;

        private TrafficLightBulbModel(TrafficLightTextureKey key) {
            this.key = key;
            separateIcon = key != null && key.getIcon() != TrafficLightIcon.NONE;
            iconTexture = separateIcon ? new ResourceLocation(TrafficEngine.MOD_ID,
                "dynamic/traffic_light/" + key.getIcon().getName() + "_" + key.getColor().getName()) : null;
            renderType = key == null ? null : RenderType.text(separateIcon ? iconTexture : key.getTextureLocation());
        }
        static TrafficLightBulbModel create(TrafficLightTextureKey key) { return new TrafficLightBulbModel(key); }
        TrafficLightTextureKey getKey() { return key; }

        void render(RenderContext<?> graphics, BlockEntity entity, int packedLight) {
            if (key == null) return;
            if (separateIcon) {
                if (!textureRegistered) {
                    // SimpleTexture reloads its mask with resource packs; no image work per frame.
                    Minecraft.getInstance().getTextureManager().register(iconTexture, new IconTexture(key.getTextureLocation()));
                    textureRegistered = true;
                }
                VertexConsumer background = graphics.multiBufferSource().getBuffer(RenderType.text(OFF_TEXTURE));
                for (int face = 0; face < 4; face++) renderFace(graphics, background, face, packedLight);
                VertexConsumer icon = graphics.multiBufferSource().getBuffer(renderType);
                graphics.poseStack().pushPose();
                graphics.poseStack().translate(0, 0, 0.001F);
                renderFace(graphics, icon, 0, LightTexture.FULL_BRIGHT);
                graphics.poseStack().popPose();
            } else {
                VertexConsumer consumer = graphics.multiBufferSource().getBuffer(renderType);
                int light = key.isOffState() ? packedLight : LightTexture.FULL_BRIGHT;
                for (int face = 0; face < 4; face++) renderFace(graphics, consumer, face, light);
            }
        }

        private void renderFace(RenderContext<?> graphics, VertexConsumer consumer, int face, int light) {
            var pose = graphics.poseStack().last();
            float[] positions = POSITIONS[face], normal = NORMALS[face];
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = vertex * 3;
                float u = UV[vertex * 2] * (face == 1 || face == 2 ? 1 / 16F : 1);
                float v = UV[vertex * 2 + 1] * (face == 3 ? 1 / 16F : 1);
                consumer.vertex(pose.pose(), positions[p], positions[p + 1], positions[p + 2])
                    .color(255, 255, 255, 255).uv(u, v).uv2(light)
                    .overlayCoords(graphics.packedOverlay()).normal(pose.normal(), normal[0], normal[1], normal[2]).endVertex();
            }
        }
    }

    /** Only the coloured glyph emits light; the shared lens underneath uses world lighting. */
    private static final class IconTexture extends SimpleTexture {
        IconTexture(ResourceLocation location) { super(location); }

        @Override protected TextureImage getTextureImage(ResourceManager resources) {
            TextureImage source = super.getTextureImage(resources);
            try (var input = resources.getResourceOrThrow(OFF_TEXTURE).open();
                 NativeImage off = NativeImage.read(input)) {
                NativeImage image = source.getImage();
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        int lens = off.getPixelRGBA(x * off.getWidth() / image.getWidth(), y * off.getHeight() / image.getHeight());
                        if ((pixel & 0xFFFFFF) == (lens & 0xFFFFFF)) image.setPixelRGBA(x, y, 0);
                    }
                }
                return source;
            } catch (IOException error) {
                source.close();
                return new TextureImage(error);
            }
        }
    }
}
