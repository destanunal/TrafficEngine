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

    // Finite enum-indexed aliases, reloaded by SimpleTexture with resource packs.
    private static final ResourceLocation[][] ROUND_TEXTURES=new ResourceLocation[TrafficLightIcon.values().length][TrafficLightColor.values().length];
    private static final boolean[][] ROUND_REGISTERED=new boolean[TrafficLightIcon.values().length][TrafficLightColor.values().length];
    private static ResourceLocation roundLocation(TrafficLightIcon icon,TrafficLightColor color) {
        int i=icon.ordinal(),c=color.ordinal();
        if(ROUND_TEXTURES[i][c]==null)ROUND_TEXTURES[i][c]=ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID,"dynamic/round_lens/"+icon.getName()+"_"+color.getName());
        return ROUND_TEXTURES[i][c];
    }
    public static ResourceLocation getRoundResourceLocation(TrafficLightIcon icon,TrafficLightColor color) {
        icon=icon.isApplicableToColor(color)?icon:TrafficLightIcon.NONE;
        ResourceLocation location=roundLocation(icon,color);
        if(!ROUND_REGISTERED[icon.ordinal()][color.ordinal()]) {
            Minecraft.getInstance().getTextureManager().register(location,new RoundLensTexture(getResourceLocation(icon,color)));
            ROUND_REGISTERED[icon.ordinal()][color.ordinal()]=true;
        }
        return location;
    }
    public static boolean usesRoundLenses(net.minecraft.world.level.block.Block block) {
        return block.getClass()==com.destan.trafficengine.block.TrafficLightBlock.class
            ||block instanceof com.destan.trafficengine.block.DoubleTrafficLightBlock
            ||block instanceof com.destan.trafficengine.block.HorizontalTrafficLightBlock;
    }
    private static boolean roundLens(RenderContext<?> graphics) {
        var entity=graphics.blockEntity();
        if(entity==null)return false;
        var block=entity.getBlockState().getBlock();
        return usesRoundLenses(block);
    }
    /** Alpha clipping occurs only when a texture is loaded/reloaded, never per frame. */
    private static final class RoundLensTexture extends SimpleTexture {
        RoundLensTexture(ResourceLocation source){super(source);}
        @Override protected TextureImage getTextureImage(ResourceManager resources) {
            TextureImage source=super.getTextureImage(resources);
            try {
                NativeImage image=source.getImage();
                int w=image.getWidth(),h=image.getHeight();
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    double dx=(2.0*x+1-w)/w,dy=(2.0*y+1-h)/h;
                    if(dx*dx+dy*dy>1)image.setPixelRGBA(x,y,0);
                }
                return source;
            }catch(IOException error){source.close();return new TextureImage(error);}
        }
    }

    private static final TrafficLightBulbModel FALLBACK_MODEL = TrafficLightBulbModel.create(null);
    private static final String TEXTURE_PATH = "block/traffic_light";
    private static final ResourceLocation OFF_TEXTURE = ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "textures/" + TEXTURE_PATH + "/off.png");
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
            return ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, String.format("textures/%s/off.png", TEXTURE_PATH));
        }
        return ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, String.format("textures/%s/%s_%s.png",
            TEXTURE_PATH,
            (key.getIcon().isApplicableToColor(key.getColor()) ? key.getIcon() : TrafficLightIcon.NONE).getName(),
            key.getColor().getName()
        ));
    }

    public static Collection<ResourceLocation> getAllTextureLocations() {
        return models.stream().map(x -> x.getKey().getTextureLocation()).toList();
    }

    public static void renderSlot(RenderContext<?> graphics, com.destan.trafficengine.block.entity.TrafficLightBlockEntity signal,
            int slot, TrafficLightIcon fallback, TrafficLightColor color, int light) {
        var symbols=signal.getSignalSymbols();
        var mask=symbols.mask(slot,signal.getCustomMask());
        if(!mask.isEmpty() && color!=TrafficLightColor.NONE) {
            int rgb=color==TrafficLightColor.RED?0xFFFF2020:color==TrafficLightColor.YELLOW?0xFFFFCF20:color==TrafficLightColor.GREEN?0xFF20FF40:-1;
            renderStencil(graphics,mask,rgb,light);
        } else render(graphics,null,symbols.icon(slot,fallback),color,light);
    }

    public static void render(RenderContext<?> graphics, BlockEntity be, TrafficLightIcon icon, TrafficLightColor color, int packedLight) {
        if (be instanceof com.destan.trafficengine.block.entity.TrafficLightBlockEntity signal && !signal.getCustomMask().isEmpty() && color != TrafficLightColor.NONE) {
            int rgb = color == TrafficLightColor.RED ? 0xFFFF2020 : color == TrafficLightColor.YELLOW ? 0xFFFFCF20 : color == TrafficLightColor.GREEN ? 0xFF20FF40 : -1;
            renderStencil(graphics, signal.getCustomMask(), rgb, packedLight); return;
        }
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
        private final RenderType roundRenderType;
        private static final RenderType ROUND_OFF_TYPE=RenderType.text(roundLocation(TrafficLightIcon.NONE,TrafficLightColor.NONE));
        private final ResourceLocation iconTexture;
        private final boolean separateIcon;
        private boolean textureRegistered;

        private TrafficLightBulbModel(TrafficLightTextureKey key) {
            this.key = key;
            separateIcon = key != null && key.getIcon() != TrafficLightIcon.NONE;
            iconTexture = separateIcon ? ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID,
                "dynamic/traffic_light/" + key.getIcon().getName() + "_" + key.getColor().getName()) : null;
            ResourceLocation asset = separateIcon ? iconTexture : key == null ? OFF_TEXTURE : key.getTextureLocation();
            renderType = key == null ? null : RenderType.text(asset);
            roundRenderType=key==null?null:RenderType.text(roundLocation(key.getIcon(),key.getColor()));
        }
        static TrafficLightBulbModel create(TrafficLightTextureKey key) { return new TrafficLightBulbModel(key); }
        TrafficLightTextureKey getKey() { return key; }

        void render(RenderContext<?> graphics, BlockEntity entity, int packedLight) {
            if (key == null) return;
            boolean round=roundLens(graphics);
            if(round)getRoundResourceLocation(TrafficLightIcon.NONE,TrafficLightColor.NONE);
            int faces=round?1:4;
            if (separateIcon) {
                if (!textureRegistered) {
                    // SimpleTexture reloads its mask with resource packs; no image work per frame.
                    Minecraft.getInstance().getTextureManager().register(iconTexture, new IconTexture(key.getTextureLocation()));
                    textureRegistered = true;
                }
                VertexConsumer background = graphics.multiBufferSource().getBuffer(round?ROUND_OFF_TYPE:RenderType.text(OFF_TEXTURE));
                for (int face = 0; face < faces; face++) renderFace(graphics, background, face, packedLight);
                if(round)renderRim(graphics,background,packedLight,false);
                VertexConsumer icon = graphics.multiBufferSource().getBuffer(renderType);
                graphics.poseStack().pushPose();
                graphics.poseStack().translate(0, 0, 0.001F);
                renderFace(graphics, icon, 0, LightTexture.FULL_BRIGHT);
                graphics.poseStack().popPose();
            } else {
                if(round)getRoundResourceLocation(key.getIcon(),key.getColor());
                VertexConsumer consumer = graphics.multiBufferSource().getBuffer(round?roundRenderType:renderType);
                int light = key.isOffState() ? packedLight : LightTexture.FULL_BRIGHT;
                for (int face = 0; face < faces; face++) renderFace(graphics, consumer, face, light);
                if(round)renderRim(graphics,consumer,light,!key.isOffState());
            }
        }

        private void renderRim(RenderContext<?> graphics,VertexConsumer consumer,int light,boolean lit) {
            var pose=graphics.poseStack().last();int shade=lit?170:255;
            for(int side=0;side<8;side++) {
                float[] positions=com.destan.trafficengine.client.model.TrafficLensGeometry.SIDES[side];
                float[] normal=com.destan.trafficengine.client.model.TrafficLensGeometry.NORMALS[side];
                for(int vertex=0;vertex<4;vertex++) {int p=vertex*3;
                    consumer.addVertex(pose.pose(),positions[p],positions[p+1],positions[p+2])
                        .setColor(shade,shade,shade,255).setUv(.5F,.5F).setOverlay(graphics.packedOverlay()).setLight(light).setNormal(pose,normal[0],normal[1],normal[2]);
                }
            }
        }

        private void renderFace(RenderContext<?> graphics, VertexConsumer consumer, int face, int light) {
            var pose = graphics.poseStack().last();
            float[] positions = POSITIONS[face], normal = NORMALS[face];
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = vertex * 3;
                float u = UV[vertex * 2] * (face == 1 || face == 2 ? 1 / 16F : 1);
                float v = UV[vertex * 2 + 1] * (face == 3 ? 1 / 16F : 1);
                consumer.addVertex(pose.pose(), positions[p], positions[p + 1], positions[p + 2])
                    .setColor(255, 255, 255, 255).setUv(u, v).setOverlay(graphics.packedOverlay()).setLight(light).setNormal(pose, normal[0], normal[1], normal[2]);
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

    private static final RenderType WARNING_LENS_OFF_TYPE = RenderType.text(ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "textures/block/warning_lens_off.png"));
    private static final RenderType WARNING_LENS_ON_TYPE = RenderType.text(ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "textures/block/warning_lens_on.png"));
    private static final RenderType STENCIL_TYPE = RenderType.text(ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "textures/block/paint_overlay.png"));
    private record StencilTexture(ResourceLocation location, RenderType renderType) {}
    // Keep texture memory bounded. Once full, new stencils use their precomputed rectangles
    // until disconnect, so a busy scene cannot cause texture eviction/upload churn.
    private static final java.util.Map<com.destan.trafficengine.data.PixelPattern, StencilTexture> STENCILS = new java.util.HashMap<>();
    private static StencilTexture stencilTexture(com.destan.trafficengine.data.PixelPattern pattern) {
        StencilTexture cached = STENCILS.get(pattern);
        if (cached != null || STENCILS.size() >= 256) return cached;
        NativeImage pixels = new NativeImage(16, 16, true);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) pixels.setPixelRGBA(x, y, pattern.pixel(x, y) ? -1 : 0);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(TrafficEngine.MOD_ID, "dynamic/stencil/" + STENCILS.size());
        Minecraft.getInstance().getTextureManager().register(location, new net.minecraft.client.renderer.texture.DynamicTexture(pixels));
        cached = new StencilTexture(location, RenderType.text(location)); STENCILS.put(pattern, cached);
        return cached;
    }
    public static void clearStencilCache() {
        for (StencilTexture texture : STENCILS.values()) Minecraft.getInstance().getTextureManager().release(texture.location());
        STENCILS.clear();
    }
    public static void renderStencil(RenderContext<?> graphics, com.destan.trafficengine.data.PixelPattern pattern, int color, int light) {
        modelsByIconAndColor[TrafficLightIcon.NONE.ordinal()][TrafficLightColor.NONE.ordinal()].render(graphics, graphics.blockEntity(), light);
        renderStencilPixels(graphics, pattern, color);
    }
    /** The unlit lens is baked into the chunk. An active lamp adds just one tinted quad. */
    public static void renderWarningLens(RenderContext<?> graphics, int color) {
        VertexConsumer consumer = graphics.multiBufferSource().getBuffer(WARNING_LENS_ON_TYPE);
        var pose = graphics.poseStack().last();
        var matrix = pose.pose();
        var normal = pose.normal();
        int red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = vertex < 2 ? 0 : 1, v = vertex == 0 || vertex == 3 ? 0 : 1;
            float x = u*4, y = v*4-4, z = 1.002F;
            consumer.addVertex(matrix.m00()*x+matrix.m10()*y+matrix.m20()*z+matrix.m30(),
                    matrix.m01()*x+matrix.m11()*y+matrix.m21()*z+matrix.m31(),
                    matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32())
                .setColor(red, green, blue, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(graphics.packedOverlay()).setNormal(normal.m20(), normal.m21(), normal.m22());
        }
    }
    public static void renderWarningPreview(net.minecraft.client.gui.GuiGraphics g, int x, int y, int size, boolean lit, int color) {
        warningPreviewQuad(g, WARNING_LENS_OFF_TYPE, x, y, size, 0xFFFFFFFF);
        if (lit) warningPreviewQuad(g, WARNING_LENS_ON_TYPE, x, y, size, color);
    }
    private static void warningPreviewQuad(net.minecraft.client.gui.GuiGraphics g, RenderType type, int x, int y, int size, int color) {
        var matrix = g.pose().last().pose();
        VertexConsumer consumer = g.bufferSource().getBuffer(type);
        int red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = vertex < 2 ? 0 : 1, v = vertex == 0 || vertex == 3 ? 0 : 1;
            float px = x+u*size, py = y+v*size;
            consumer.addVertex(matrix.m00()*px+matrix.m10()*py+matrix.m30(),
                    matrix.m01()*px+matrix.m11()*py+matrix.m31(), matrix.m02()*px+matrix.m12()*py+matrix.m32())
                .setColor(red, green, blue, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
        }
    }
    private static void renderStencilPixels(RenderContext<?> graphics, com.destan.trafficengine.data.PixelPattern pattern, int color) {
        if (pattern.isEmpty()) return;
        StencilTexture texture = stencilTexture(pattern);
        VertexConsumer consumer = graphics.multiBufferSource().getBuffer(texture == null ? STENCIL_TYPE : texture.renderType());
        var pose = graphics.poseStack().last();
        int red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
        if (texture != null) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float u = vertex < 2 ? 0 : 1, v = vertex == 0 || vertex == 3 ? 0 : 1;
                consumer.addVertex(pose.pose(), u * 4, v * 4 - 4, 1.002F).setColor(red, green, blue, 255).setUv(u, v)
                    .setLight(LightTexture.FULL_BRIGHT).setOverlay(graphics.packedOverlay()).setNormal(pose, 0, 0, 1);
            }
            return;
        }
        var rectangles = pattern.rectangles();
        for (int i = 0; i < rectangles.size(); i++) {
            var rect = rectangles.get(i);
            float left = rect.x() * .25F, right = (rect.x() + rect.width()) * .25F;
            float top = rect.y() * .25F - 4, bottom = (rect.y() + rect.height()) * .25F - 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = vertex < 2 ? left : right, y = vertex == 0 || vertex == 3 ? top : bottom;
                consumer.addVertex(pose.pose(), x, y, 1.002F).setColor(red, green, blue, 255).setUv(.5F, .5F)
                    .setLight(LightTexture.FULL_BRIGHT).setOverlay(graphics.packedOverlay()).setNormal(pose, 0, 0, 1);
            }
        }
    }

}
