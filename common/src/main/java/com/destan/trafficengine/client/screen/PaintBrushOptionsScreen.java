package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.item.RoadStrokeGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PaintBrushOptionsScreen extends ScaledTrafficEngineScreen {
    private final PaintBrushScreen parent;
    private static final String KEY = "gui.trafficengine.paint_brush.";
    private int controlsWidth, previewX, previewWidth, widthControlsY;
    private boolean showLanePattern, showLinePosition;

    public PaintBrushOptionsScreen(PaintBrushScreen parent) {
        super(Component.translatable(KEY+"options")); this.parent=parent;
    }

    private Component text(String suffix) { return Component.translatable(KEY+suffix); }
    private void choice(int x,int y,int width,Component caption,boolean selected,Runnable action) {
        addRenderableWidget(new TEButton(x,y,width,22,caption,b->{parent.areaStyle=0;action.run();refreshEditor();}).selected(selected));
    }

    @Override protected void initEditor() {
        layoutWindow(536,330); addCloseButton();
        controlsWidth=318; previewX=left+controlsWidth+30; previewWidth=windowWidth-controlsWidth-44;
        showLanePattern=parent.lineStyle==1;
        showLinePosition=parent.lineStyle>=0;
        widthControlsY=top+(showLanePattern?271:219);
        int x=left+16;
        choice(x,top+63,154,text("mode.single.short"),parent.areaStyle==0 && !parent.batchPainting,()->parent.batchPainting=false);
        choice(x+160,top+63,154,text("mode.batch.short"),parent.areaStyle==0 && parent.batchPainting,()->parent.batchPainting=true);
        choice(x,top+115,154,text("connection.off.short"),parent.lineStyle<0,()->parent.lineStyle=-1);
        choice(x+160,top+115,154,text("connection.on.short"),parent.lineStyle>=0,()->parent.lineStyle=0);
        for(int i=0;i<4;i++) {
            final int style=i;
            TEButton button=addRenderableWidget(new TEButton(x+i*80,top+167,74,22,text("style."+i),b->{parent.areaStyle=0;parent.lineStyle=style;refreshEditor();})
                .selected(parent.lineStyle==i));
            button.active=parent.lineStyle>=0;
        }
        if(showLanePattern) {
            choice(x,top+219,154,text("dash.city"),parent.batchPainting && parent.dashGap==2,()->{
                parent.batchPainting=true;parent.dashGap=2;
            });
            choice(x+160,top+219,154,text("dash.highway"),parent.batchPainting && parent.dashGap==4,()->{
                parent.batchPainting=true;parent.dashGap=4;
            });
        }
        for(int i=0;i<5;i++) {
            final int value=1+i*2;
            TEButton button=addRenderableWidget(new TEButton(x+i*64,widthControlsY,58,22,Component.translatable(KEY+"width.short",value),b->{parent.areaStyle=0;parent.paintWidth=value;refreshEditor();})
                .selected(parent.paintWidth==value));
            button.active=parent.batchPainting;
        }
        if(showLinePosition) {
            int[] positions={1,0,2};
            int positionWidth=(previewWidth-24)/3;
            for(int i=0;i<positions.length;i++) {
                final int value=positions[i];
                addRenderableWidget(new TEButton(previewX+10+i*(positionWidth+2),top+174,
                    positionWidth,22,text("position."+value),b->{parent.areaStyle=0;parent.linePosition=value;refreshEditor();})
                    .selected(parent.linePosition==value));
            }
        }
        addRenderableWidget(new TEButton(left+windowWidth-106,top+windowHeight-30,90,22,Component.translatable("gui.done"),b->onClose()).primary());
    }

    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        beginWindow(g);
        int x=left+16;
        g.drawString(font,text("mode.title"),x,top+46,TEColors.MUTED,false);
        g.drawString(font,text("connection.title"),x,top+98,TEColors.MUTED,false);
        g.drawString(font,text("style.title"),x,top+150,TEColors.MUTED,false);
        if(showLanePattern) g.drawString(font,text("dash.title"),x,top+202,TEColors.MUTED,false);
        g.drawString(font,Component.translatable(KEY+"width",parent.batchPainting?parent.paintWidth:1),x,widthControlsY-17,TEColors.MUTED,false);
        TEPanel.draw(g,previewX,top+46,previewWidth,244);
        g.drawString(font,text("preview"),previewX+10,top+56,TEColors.ACCENT,false);
        drawRoadPreview(g);
        g.drawWordWrap(font,text("connection."+(parent.lineStyle+1)),previewX+10,top+134,previewWidth-20,TEColors.TEXT);
        if(showLinePosition) g.drawString(font,text("position.title"),previewX+10,top+160,TEColors.MUTED,false);
        int usageY=top+(showLinePosition?210:160);
        g.drawString(font,text("usage.title"),previewX+10,usageY,TEColors.ACCENT,false);
        g.drawWordWrap(font,text(parent.batchPainting?"usage.batch":"usage.single"),previewX+10,usageY+16,previewWidth-20,TEColors.TEXT);
        finishWindow(g,mx,my,tick);
    }

    private void drawRoadPreview(GuiGraphics g) {
        int x=previewX+10,y=top+80,w=previewWidth-20,h=46;
        g.fill(x,y,x+w,y+h,0xFF1C1E20);
        int style=parent.lineStyle;
        if(style<0) {
            g.drawCenteredString(font,text("preview.pattern"),x+w/2,y+(h-font.lineHeight)/2,TEColors.TEXT);return;
        }
        int lanes=parent.batchPainting?Math.min(3,parent.paintWidth):1;
        int thickness=style==0?2:4;
        int shift=(int)Math.round(RoadStrokeGeometry.positionOffset(parent.linePosition)*42);
        int maxShift=h/2-(lanes/2)*12-(style==2?5:thickness/2);
        shift=Math.max(-maxShift,Math.min(maxShift,shift));
        for(int lane=0;lane<lanes;lane++) {
            int cy=y+h/2+(lane-lanes/2)*12+shift;
            if(style==2) {
                g.fill(x+4,cy-5,x+w-4,cy-1,0xFFF4F6EE);
                g.fill(x+4,cy+1,x+w-4,cy+5,0xFFF4F6EE);
            } else if(style==1) {
                float unit=(w-8)/12F;
                for(int start=0;start+2<=12;start+=2+parent.dashGap)
                    g.fill(x+4+Math.round(start*unit),cy-thickness/2,x+4+Math.round((start+2)*unit),cy+thickness/2,0xFFF4F6EE);
            } else g.fill(x+4,cy-thickness/2,x+w-4,cy+thickness/2,0xFFF4F6EE);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
