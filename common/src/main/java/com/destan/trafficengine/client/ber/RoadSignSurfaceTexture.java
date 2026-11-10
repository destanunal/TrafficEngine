package com.destan.trafficengine.client.ber;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Dedicated filtered atlas; never changes the sampler/settings of Minecraft's block atlas. */
final class RoadSignSurfaceTexture extends AbstractTexture {
    static final ResourceLocation LOCATION = new ResourceLocation("trafficengine", "dynamic/road_sign_surfaces");
    private static boolean registered;
    static void ensureRegistered() {
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(LOCATION, new RoadSignSurfaceTexture());
            registered = true;
        }
    }
    @Override public void load(ResourceManager resources) {
        int[][] levels = RoadSignSurface.pixels();
        TextureUtil.prepareImage(getId(), RoadSignSurface.LEVELS, RoadSignSurface.WIDTH, RoadSignSurface.HEIGHT);
        for (int level=0; level<levels.length; level++) {
            int w=RoadSignSurface.WIDTH>>level,h=RoadSignSurface.HEIGHT>>level;
            try (NativeImage image=new NativeImage(w,h,false)) {
                int[] pixels=levels[level];
                for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    int argb=pixels[y*w+x];
                    image.setPixelRGBA(x,y,(argb&0xFF00FF00)|((argb>>>16)&255)|((argb&255)<<16));
                }
                image.upload(level,0,0,0,0,w,h,true,true,true,false);
            }
        }
        setFilter(true,true);
    }
}
