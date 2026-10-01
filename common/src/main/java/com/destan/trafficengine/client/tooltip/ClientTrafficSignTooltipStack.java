package com.destan.trafficengine.client.tooltip;

import java.util.Map;

import net.minecraft.network.chat.Component;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.data.TrafficSignClientTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;

public class ClientTrafficSignTooltipStack implements ClientTooltipComponent {

    private final NonNullList<NamedTrafficSignTextureReference> patterns;
    private final NamedTrafficSignTextureReference selectedData;
    private final Map<NamedTrafficSignTextureReference, TrafficSignClientTexture> textures;

    private static final float FONT_SCALE = 0.75f;

    private int lastKnownTexturesCount=-1, columns=1, rows=0;

    public ClientTrafficSignTooltipStack(TrafficSignTooltip pTrafficSignTooltip) {
        this.patterns = pTrafficSignTooltip.getPatterns();
        this.selectedData = pTrafficSignTooltip.getSelected();
        this.textures = pTrafficSignTooltip.getTextures();
    }

    public int getHeight() {
        checkGridLayout();
        return rows * 18 + (selectedData == null ? 0 : Minecraft.getInstance().font.lineHeight * 2 + 24);
    }

    public int getWidth(Font pFont) {
        checkGridLayout();
        return columns * 18;
    }

    private void checkGridLayout() {
        if (lastKnownTexturesCount != patterns.size()) {
            lastKnownTexturesCount = patterns.size();
            rows=lastKnownTexturesCount==0?0:(int)Math.sqrt(lastKnownTexturesCount);
            columns=rows==0?1:(lastKnownTexturesCount+rows-1)/rows;
        }
    }

    @Override
    public void renderImage(Font pFont, int pX, int pY, GuiGraphics guiGraphics) {
        checkGridLayout();
        int x = pX;
        int y = pY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(FONT_SCALE, FONT_SCALE, FONT_SCALE);
        guiGraphics.pose().translate(0, 0, 1000);
        if (selectedData != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((x + 5) / FONT_SCALE, y / FONT_SCALE, 0);
            guiGraphics.drawString(pFont, Component.translatable("item.trafficengine.pattern_catalogue.tooltip.selected_texture"),0,0,0xFFDBDBDB,false);
            guiGraphics.drawString(pFont,selectedData.getName(),32,pFont.lineHeight+10,0xFFFFFFFF,false);
            guiGraphics.pose().popPose();
        }
        if (lastKnownTexturesCount > 0) {            
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((x + 5) / FONT_SCALE, (y + pFont.lineHeight + 24) / FONT_SCALE, 0);
            guiGraphics.drawString(pFont,Component.translatable("item.trafficengine.pattern_catalogue.tooltip.saved_textures"),0,0,0xFFDBDBDB,false);
            guiGraphics.pose().popPose();
        }
        guiGraphics.pose().popPose();

        if (selectedData != null) {
            renderTexture(guiGraphics, x + 10, y + pFont.lineHeight, selectedData);
        }

        y += pFont.lineHeight * 2 + 24;
        for (int i = 0, k = 0; i < columns && k < lastKnownTexturesCount; i++) {
            for (int j = 0; j < rows && k < lastKnownTexturesCount; j++, k++) {
                final int n = k;      
                final NamedTrafficSignTextureReference textureData = this.patterns.get(n);
                renderTexture(guiGraphics, x + 10 + (i * 18), y + (j * 18), textureData);
            }
        }
    }    

    private void renderTexture(GuiGraphics guiGraphics, int x, int y, NamedTrafficSignTextureReference data) {
        TrafficSignClientTexture texture = textures.get(data);
        if (texture != null) {
            int w = texture.getRawData().getWidth();
            int h = texture.getRawData().getHeight();
            guiGraphics.blit(texture.getTextureLocation(), x, y, 16, 16, 0, 0, w, h, w, h);
        }
    }
}
