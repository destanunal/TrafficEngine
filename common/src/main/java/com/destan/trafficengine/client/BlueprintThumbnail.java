package com.destan.trafficengine.client;

import com.destan.trafficengine.blueprint.BlueprintPlan;
import com.destan.trafficengine.data.SafeDynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Small menu thumbnail rasterized/uploaded only when its plan or rotation changes. */
public final class BlueprintThumbnail implements AutoCloseable {
    private static final int W=192,H=128;
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("trafficengine","blueprint_thumbnail");
    private BlueprintPlan cached;private int turns=-1;private boolean owned;
    private static double px(double x,double z){return (x-z)*.8660254;}
    private static double py(double x,double y,double z){return (x+z)*.5-y*1.2;}
    public void update(BlueprintPlan plan,int turns){
        if(cached==plan&&this.turns==turns&&owned)return;close();if(plan==null)return;
        var cells=plan.rotated(turns);Map<BlockState,float[]> meshes=new HashMap<>();double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for(var cell:cells){float[] edges=meshes.computeIfAbsent(cell.state(),BlueprintPreview::geometry);
            for(int i=0;i<edges.length;i+=9)for(int end=0;end<2;end++){int p=i+end*3;double x=cell.pos().getX()+edges[p],y=cell.pos().getY()+edges[p+1],z=cell.pos().getZ()+edges[p+2];double a=px(x,z),b=py(x,y,z);x0=Math.min(x0,a);y0=Math.min(y0,b);x1=Math.max(x1,a);y1=Math.max(y1,b);}}
        if(!Double.isFinite(x0))return;
        double scale=Math.min((W-12)/Math.max(.1,x1-x0),(H-12)/Math.max(.1,y1-y0)),ox=(W-(x1-x0)*scale)/2,oy=(H-(y1-y0)*scale)/2;
        NativeImage image=new NativeImage(W,H,true);
        for(var cell:cells){float[] edges=meshes.get(cell.state());for(int i=0;i<edges.length;i+=9){int[] points=new int[4];for(int end=0;end<2;end++){int p=i+end*3;double x=cell.pos().getX()+edges[p],y=cell.pos().getY()+edges[p+1],z=cell.pos().getZ()+edges[p+2];points[end*2]=(int)Math.round(ox+(px(x,z)-x0)*scale);points[end*2+1]=(int)Math.round(oy+(py(x,y,z)-y0)*scale);}line(image,points[0],points[1],points[2],points[3]);}}
        Minecraft.getInstance().getTextureManager().register(ID,new SafeDynamicTexture(image));owned=true;cached=plan;this.turns=turns;
    }
    private static void line(NativeImage image,int x,int y,int endX,int endY){
        int dx=Math.abs(endX-x),sx=x<endX?1:-1,dy=-Math.abs(endY-y),sy=y<endY?1:-1,error=dx+dy;
        while(true){if(x>=0&&y>=0&&x<W&&y<H)image.setPixelRGBA(x,y,0xFFF0D070);if(x==endX&&y==endY)break;int twice=2*error;if(twice>=dy){error+=dy;x+=sx;}if(twice<=dx){error+=dx;y+=sy;}}
    }
    public void render(GuiGraphics g,int x,int y,int width,int height){if(owned)g.blit(ID,x,y,width,height,0,0,W,H,W,H);}
    @Override public void close(){if(owned)Minecraft.getInstance().getTextureManager().release(ID);owned=false;cached=null;turns=-1;}
}
