package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.RoadAreaPreview;
import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.item.RoadAreaGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PaintBrushAreaScreen extends ScaledTrafficEngineScreen {
    private static final String KEY="gui.trafficengine.paint_brush.area.";
    private final PaintBrushScreen parent;
    private int shape,style,previewX,previewWidth;
    private RoadAreaGeometry.Result preview;

    public PaintBrushAreaScreen(PaintBrushScreen parent) {
        super(Component.translatable(KEY+"title"));this.parent=parent;
        shape=parent.areaShape;style=parent.areaStyle>0?parent.areaStyle:RoadAreaGeometry.CROSSHATCH;
    }
    private Component text(String suffix) { return Component.translatable(KEY+suffix); }
    @Override protected float preferredScale() { return .94F; }
    @Override protected void initEditor() {
        layoutWindow(536,330);addCloseButton();
        int x=left+16;previewX=left+348;previewWidth=windowWidth-362;
        for(int i=0;i<3;i++) {
            final int value=i;
            addRenderableWidget(new TEButton(x+i*106,top+72,100,22,text("shape."+i),b->{shape=value;refreshEditor();}).selected(shape==value));
            final int paintStyle=i+1;
            addRenderableWidget(new TEButton(x+i*106,top+132,100,22,text("style."+paintStyle),b->{style=paintStyle;refreshEditor();}).selected(style==paintStyle));
        }
        preview=RoadAreaPreview.sample(shape,style);
        addRenderableWidget(new TEButton(left+windowWidth-106,top+windowHeight-30,90,22,Component.translatable("gui.done"),b->onClose()).primary());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        beginWindow(g);
        int x=left+16;
        TEText.drawString(g, font,text("shape.title"),x,top+54,TEColors.MUTED,false);
        TEText.drawString(g, font,text("style.title"),x,top+114,TEColors.MUTED,false);
        TEText.drawString(g, font,text("color.title"),x,top+182,TEColors.MUTED,false);
        g.fill(x,top+202,x+12,top+214,parent.paintTint());
        TEText.drawString(g, font,parent.paintColor().getValueTranslation(),x+20,top+203,TEColors.TEXT,false);
        TEText.drawWordWrap(g, font,text("usage."+shape),x,top+248,314,TEColors.TEXT);
        TEText.drawString(g, font,Component.translatable("gui.trafficengine.paint_brush.usage.clear"),x,top+280,TEColors.MUTED,false);
        TEPanel.draw(g,previewX,top+46,previewWidth,244);
        TEText.drawString(g, font,Component.translatable("gui.trafficengine.paint_brush.preview"),previewX+10,top+56,TEColors.ACCENT,false);
        RoadAreaPreview.draw(g,preview,previewX+10,top+80,previewWidth-20,174,parent.paintTint());
        TEText.drawCenteredString(g, font,Component.translatable(KEY+"corners",RoadAreaGeometry.cornerCount(shape)),previewX+previewWidth/2,top+268,TEColors.MUTED);
        finishWindow(g,mx,my,tick);
    }
    @Override public void onClose() {
        parent.areaShape=shape;parent.areaStyle=style;
        minecraft.setScreen(parent);
    }
}
