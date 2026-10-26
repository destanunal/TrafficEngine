package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.data.PixelPattern;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Menu silhouettes come from the actual lamp textures, with one bounded cache per resource reload. */
public final class TESignalIcons {
    private static final TrafficLightIcon[] PRESETS=TrafficLightIcon.values();
    private static final ResourceLocation OFF=location("off");
    private static final ResourceLocation[] SOURCES=new ResourceLocation[PRESETS.length];
    private static PixelPattern[] patterns=emptyPatterns();
    private static volatile int revision;
    private static int loadedRevision=-1;
    static {for(var icon:PRESETS)if(icon!=TrafficLightIcon.NONE)SOURCES[icon.ordinal()]=location(icon.getName()+"_green");}
    private TESignalIcons(){}
    private static ResourceLocation location(String name){return ResourceLocation.fromNamespaceAndPath("trafficengine","textures/block/traffic_light/"+name+".png");}
    private static PixelPattern[] emptyPatterns(){var p=new PixelPattern[PRESETS.length];Arrays.fill(p,PixelPattern.EMPTY);return p;}
    public static void invalidate(){revision++;}
    private static void reload(){
        int current=revision;PixelPattern[] next=emptyPatterns();
        var resources=Minecraft.getInstance().getResourceManager();
        try(var input=resources.getResourceOrThrow(OFF).open();NativeImage off=NativeImage.read(input)){
            for(var icon:PRESETS)if(icon!=TrafficLightIcon.NONE){
                try(var source=resources.getResourceOrThrow(SOURCES[icon.ordinal()]).open();NativeImage image=NativeImage.read(source)){
                    next[icon.ordinal()]=fromTexture(image,off);
                }
            }
        }catch(IOException error){com.mojang.logging.LogUtils.getLogger().warn("Could not load traffic signal menu silhouettes",error);}
        patterns=next;loadedRevision=current;
    }
    static PixelPattern fromTexture(NativeImage image,NativeImage off){
        long[] bits=new long[4];
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int sx=x*image.getWidth()/16,sy=y*image.getHeight()/16;
            int pixel=image.getPixelRGBA(sx,sy);
            int lens=off.getPixelRGBA(sx*off.getWidth()/image.getWidth(),sy*off.getHeight()/image.getHeight());
            if((pixel>>>24)!=0&&(pixel&0xFFFFFF)!=(lens&0xFFFFFF)){int p=y*16+x;bits[p>>>6]|=1L<<(p&63);}
        }
        return PixelPattern.of(bits);
    }
    public static void draw(GuiGraphics g,TrafficLightIcon icon,int x,int y,int color){
        if(icon==TrafficLightIcon.NONE)return;
        if(loadedRevision!=revision)reload();
        var quads=patterns[icon.ordinal()].rectangles();if(quads.isEmpty())return;
        var matrix=g.pose().last().pose();var vertices=g.bufferSource().getBuffer(RenderType.gui());
        for(int i=0;i<quads.size();i++){
            var rect=quads.get(i);
            float left=x+rect.x(),top=y+rect.y(),right=left+rect.width(),bottom=top+rect.height();
            vertex(vertices,matrix,left,top,color);vertex(vertices,matrix,left,bottom,color);
            vertex(vertices,matrix,right,bottom,color);vertex(vertices,matrix,right,top,color);
        }
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vertices,org.joml.Matrix4f m,float x,float y,int color){
        vertices.addVertex(m.m00()*x+m.m10()*y+m.m30(),m.m01()*x+m.m11()*y+m.m31(),m.m02()*x+m.m12()*y+m.m32())
            .setColor(color>>>16&255,color>>>8&255,color&255,color>>>24);
    }
}
