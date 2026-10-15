package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class PaintBrushOptionsScreen extends ScaledTrafficEngineScreen {
    private final PaintBrushScreen parent;
    private static final String KEY = "gui.trafficengine.paint_brush.";
    private int controlsWidth, previewX, previewWidth, widthControlsY;
    private boolean showLanePattern, showLinePosition;
    private com.destan.trafficengine.item.RoadAreaGeometry.Result pathPreview;

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
        parent.linePosition=com.destan.trafficengine.item.RoadPaintPlan.normalizePosition(parent.batchPainting,parent.paintWidth,parent.pathShape,parent.linePosition);
        showLinePosition=parent.lineStyle>=0 && (!parent.batchPainting || parent.pathShape==0 && parent.paintWidth==1);
        widthControlsY=top+(showLanePattern?282:238);
        pathPreview=parent.batchPainting && parent.pathShape>0
            ?com.destan.trafficengine.client.gui.RoadAreaPreview.curveSample(parent.pathShape,parent.paintWidth,parent.lineStyle,parent.dashGap,parent.linePosition):null;
        int x=left+16;
        choice(x,top+60,154,text("mode.single.short"),parent.areaStyle==0 && !parent.batchPainting,()->{parent.batchPainting=false;parent.pathShape=0;});
        choice(x+160,top+60,154,text("mode.batch.short"),parent.areaStyle==0 && parent.batchPainting,()->parent.batchPainting=true);
        choice(x,top+106,154,text("connection.off.short"),parent.lineStyle<0,()->{parent.lineStyle=-1;parent.pathShape=0;});
        choice(x+160,top+106,154,text("connection.on.short"),parent.lineStyle>=0,()->parent.lineStyle=0);
        for(int i=0;i<4;i++) {
            final int style=i;
            TEButton button=addRenderableWidget(new TEButton(x+i*80,top+150,74,22,text("style."+i),b->{parent.areaStyle=0;parent.lineStyle=style;refreshEditor();})
                .selected(parent.lineStyle==i));
            button.active=parent.lineStyle>=0;
        }
        for(int i=0;i<3;i++) {
            final int shape=i;
            TEButton option=addRenderableWidget(new TEButton(x+i*106,top+194,100,22,text("path."+i),b->{
                parent.areaStyle=0;parent.pathShape=shape;
                if(shape>0) {parent.lineStyle=Math.max(0,parent.lineStyle);parent.linePosition=0;}
                refreshEditor();
            }).selected(parent.pathShape==i));
            option.active=parent.batchPainting;
        }
        if(showLanePattern) {
            choice(x,top+238,154,text("dash.city"),parent.batchPainting && parent.dashGap==2,()->{
                parent.batchPainting=true;parent.dashGap=2;
            });
            choice(x+160,top+238,154,text("dash.highway"),parent.batchPainting && parent.dashGap==4,()->{
                parent.batchPainting=true;parent.dashGap=4;
            });
        }
        for(int i=0;i<4;i++) {
            final int value=1+i*2;
            TEButton button=addRenderableWidget(new TEButton(x+i*80,widthControlsY,74,22,Component.translatable(KEY+"width.short",value),b->{parent.areaStyle=0;parent.paintWidth=value;refreshEditor();})
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
        g.drawString(font,text("connection.title"),x,top+92,TEColors.MUTED,false);
        g.drawString(font,text("style.title"),x,top+136,TEColors.MUTED,false);
        g.drawString(font,text("path.title"),x,top+180,TEColors.MUTED,false);
        if(showLanePattern) g.drawString(font,text("dash.title"),x,top+224,TEColors.MUTED,false);
        g.drawString(font,Component.translatable(KEY+"width",parent.batchPainting?parent.paintWidth:1),x,widthControlsY-17,TEColors.MUTED,false);
        TEPanel.draw(g,previewX,top+46,previewWidth,244);
        g.drawString(font,text("preview"),previewX+10,top+56,TEColors.ACCENT,false);
        drawRoadPreview(g);
        g.drawWordWrap(font,text("connection."+(parent.lineStyle+1)),previewX+10,top+(parent.batchPainting && parent.paintWidth>1?182:134),previewWidth-20,TEColors.TEXT);
        if(showLinePosition) g.drawString(font,text("position.title"),previewX+10,top+160,TEColors.MUTED,false);
        int usageY=top+(showLinePosition || parent.batchPainting && parent.paintWidth>1?210:160);
        g.drawString(font,text("usage.title"),previewX+10,usageY,TEColors.ACCENT,false);
        g.drawWordWrap(font,text(!parent.batchPainting?"usage.single":parent.pathShape==1?"usage.curve":parent.pathShape==2?"usage.roundabout":"usage.batch"),previewX+10,usageY+16,previewWidth-20,TEColors.TEXT);
        finishWindow(g,mx,my,tick);
    }

    private void drawRoadPreview(GuiGraphics g) {
        int x=previewX+10,y=top+80,w=previewWidth-20,h=parent.batchPainting && parent.paintWidth>1?94:46;
        g.fill(x,y,x+w,y+h,0xFF1C1E20);
        if(pathPreview!=null) {com.destan.trafficengine.client.gui.RoadAreaPreview.draw(g,pathPreview,x,y,w,h,parent.paintTint());return;}
        int style=parent.lineStyle;
        if(style<0) {
            g.drawCenteredString(font,text("preview.pattern"),x+w/2,y+(h-font.lineHeight)/2,TEColors.TEXT);return;
        }
        com.destan.trafficengine.client.gui.RoadLinePreview.straight(g,x,y,w,h,style,
            parent.batchPainting?parent.paintWidth:1,parent.dashGap,parent.linePosition,false,parent.paintTint());
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
