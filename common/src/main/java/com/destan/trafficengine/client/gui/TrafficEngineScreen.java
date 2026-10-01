package com.destan.trafficengine.client.gui;

import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class TrafficEngineScreen extends Screen {
    protected int left, top, windowWidth, windowHeight;
    protected TrafficEngineScreen(Component title) { super(title); }
    protected void layoutWindow(int preferredWidth, int preferredHeight) {
        windowWidth = Math.min(preferredWidth, width - 12);
        windowHeight = Math.min(preferredHeight, height - 12);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
    }
    protected void addCloseButton() {
        addRenderableWidget(new TEButton(left + windowWidth - 29, top + 8, 21, 19,
            Component.literal("×"), b -> onClose()));
    }
    protected void renderWindow(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x801A2028);
        TEChrome.draw(g, font, left, top, windowWidth, windowHeight, title);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Our background is drawn before the window. NeoForge's Screen.render
        // invokes this afterwards, which would blur the already drawn UI.
    }

}
