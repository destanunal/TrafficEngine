package com.destan.trafficengine.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public record GuiIcon(ResourceLocation location,int u,int v,int width,int height,int textureWidth,int textureHeight) {
    public void render(GuiGraphics g,int x,int y,int w,int h) {g.blit(location,x,y,w,h,u,v,width,height,textureWidth,textureHeight);}
}
