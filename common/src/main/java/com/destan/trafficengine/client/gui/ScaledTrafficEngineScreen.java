package com.destan.trafficengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Keeps editor columns and their hit targets together at smaller GUI resolutions. */
public abstract class ScaledTrafficEngineScreen extends TrafficEngineScreen {
    private float uiScale = 1;
    protected ScaledTrafficEngineScreen(Component title) { super(title); }

    @Override protected final void init() {
        int actualWidth = width, actualHeight = height;
        uiScale = Math.min(1, Math.min(width / 608F, height / 356F));
        width = Math.round(width / uiScale); height = Math.round(height / uiScale);
        initEditor();
        width = actualWidth; height = actualHeight;
    }

    protected abstract void initEditor();
    protected void refreshEditor() { clearWidgets(); init(); }
    protected void beginWindow(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x801A2028);
        g.pose().pushPose(); g.pose().scale(uiScale, uiScale, 1);
        TEChrome.draw(g, font, left, top, windowWidth, windowHeight, title);
    }
    protected void finishWindow(GuiGraphics g, int x, int y, float tick) {
        super.render(g, (int)(x/uiScale), (int)(y/uiScale), tick);
        g.pose().popPose();
    }
    protected double editorCoordinate(double value) { return value/uiScale; }
    @Override public boolean mouseClicked(double x,double y,int button) { return super.mouseClicked(x/uiScale,y/uiScale,button); }
    @Override public boolean mouseReleased(double x,double y,int button) { return super.mouseReleased(x/uiScale,y/uiScale,button); }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale); }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double delta) { return super.mouseScrolled(x/uiScale,y/uiScale,horizontal,delta); }
    @Override public void mouseMoved(double x,double y) { super.mouseMoved(x/uiScale,y/uiScale); }
}
