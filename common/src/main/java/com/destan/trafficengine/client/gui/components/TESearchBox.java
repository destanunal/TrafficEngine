package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TESearchBox extends EditBox {
    private final Font textFont;
    private Component placeholder;
    public TESearchBox(Font font, int x, int y, int width, int height, Component hint) {
        super(font, x, y, width, height, hint);
        textFont=font;
        setHint(hint);
        setMaxLength(80);
        setTextColor(TEColors.TEXT);
        setTextColorUneditable(TEColors.MUTED);
        setBordered(false);
    }
    @Override public void setHint(Component hint){super.setHint(hint);placeholder=hint;}
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TEPanel.surface(g, getX() - 5, getY() - 5, width + 10, height + 10,
            TEColors.FIELD, isFocused() ? TEColors.SELECTION_BORDER : TEColors.BORDER);
        g.pose().pushPose();
        float textY=com.destan.trafficengine.client.gui.TEText.centeredY(g,textFont,getValue().isEmpty()&&placeholder!=null?placeholder:getValue(),getY()-5,height+10);
        g.pose().translate(0,textY-getY(),0);
        try {super.renderWidget(g,mouseX,mouseY,partialTick);}
        finally {g.pose().popPose();}
    }
}
