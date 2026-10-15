package com.destan.trafficengine.client.gui;

import java.util.List;
import com.destan.trafficengine.item.RoadAreaGeometry;
import com.destan.trafficengine.item.RoadPaintPlan.Cell;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/** The preview and world use the same clipped polygons, including the border and hatch spacing. */
public final class RoadAreaPreview {
    private RoadAreaPreview() {}
    private static RoadAreaGeometry.Result measured;
    private static int minX,minZ,maxX,maxZ;
    public static RoadAreaGeometry.Result sample(int shape,int style) {
        List<Cell> corners=switch(shape) {
            case RoadAreaGeometry.TRIANGLE -> List.of(new Cell(0,0,7),new Cell(4,0,0),new Cell(9,0,7));
            case RoadAreaGeometry.QUADRILATERAL -> List.of(new Cell(1,0,0),new Cell(9,0,2),new Cell(7,0,7),new Cell(0,0,5));
            default -> List.of(new Cell(0,0,0),new Cell(9,0,7));
        };
        return RoadAreaGeometry.between(corners,shape,style);
    }
    public static RoadAreaGeometry.Result curveSample(int shape,int style,int dashGap,int position) {
        return curveSample(shape,1,style,dashGap,position);
    }
    public static RoadAreaGeometry.Result curveSample(int shape,int width,int style,int dashGap,int position) {
        width=com.destan.trafficengine.item.RoadPaintPlan.normalizeWidth(width);
        int radius=2+width/2*2,span=8+width/2*3;
        List<Cell> points=switch(shape) {
            case com.destan.trafficengine.item.RoadCurveGeometry.ROUNDABOUT -> List.of(new Cell(-radius,0,0),new Cell(0,0,-radius),new Cell(radius,0,0),new Cell(0,0,radius));
            default -> List.of(new Cell(0,0,span/2),new Cell(span,0,0),new Cell(span*2,0,span/2));
        };
        var geometry=com.destan.trafficengine.item.RoadCurveGeometry.preview(points,shape,width,Math.max(0,style),dashGap,position);
        return new RoadAreaGeometry.Result(geometry,List.copyOf(geometry.keySet()));
    }
    public static void draw(GuiGraphics g,RoadAreaGeometry.Result sample,int x,int y,int width,int height,int tint) {
        g.fill(x,y,x+width,y+height,0xFF1C1E20);
        if(sample==null || sample.isEmpty())return;
        if(measured!=sample) {
            minX=minZ=Integer.MAX_VALUE;maxX=maxZ=Integer.MIN_VALUE;
            for(Cell cell:sample.geometry().keySet()) {
                minX=Math.min(minX,cell.x());minZ=Math.min(minZ,cell.z());
                maxX=Math.max(maxX,cell.x()+1);maxZ=Math.max(maxZ,cell.z()+1);
            }
            measured=sample;
        }
        float scale=Math.min((width-8)/(float)(maxX-minX),(height-8)/(float)(maxZ-minZ));
        float ox=x+(width-(maxX-minX)*scale)/2-minX*scale,oy=y+(height-(maxZ-minZ)*scale)/2-minZ*scale;
        g.flush();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        var pose=g.pose().last().pose();
        sample.geometry().forEach((cell,vertices)->{
            for(int i=0;i<vertices.length;i+=3)buffer.vertex(pose,ox+(cell.x()+vertices[i])*scale,
                    oy+(cell.z()+vertices[i+2])*scale,0).color(tint).endVertex();
        });
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
