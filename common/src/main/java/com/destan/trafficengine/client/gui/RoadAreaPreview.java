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
    public static RoadAreaGeometry.Result sample(int shape,int style) {
        List<Cell> corners=switch(shape) {
            case RoadAreaGeometry.TRIANGLE -> List.of(new Cell(0,0,7),new Cell(4,0,0),new Cell(9,0,7));
            case RoadAreaGeometry.QUADRILATERAL -> List.of(new Cell(1,0,0),new Cell(9,0,2),new Cell(7,0,7),new Cell(0,0,5));
            default -> List.of(new Cell(0,0,0),new Cell(9,0,7));
        };
        return RoadAreaGeometry.between(corners,shape,style);
    }
    public static void draw(GuiGraphics g,RoadAreaGeometry.Result sample,int x,int y,int width,int height,int tint) {
        g.fill(x,y,x+width,y+height,0xFF303539);
        if(sample==null || sample.isEmpty())return;
        float scale=Math.min((width-8)/10F,(height-8)/8F);
        float ox=x+(width-10*scale)/2,oy=y+(height-8*scale)/2;
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
