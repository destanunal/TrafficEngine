package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.network.packets.cts.WritableSignPacket;
import com.destan.trafficengine.network.packets.cts.TownSignPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.block.entity.WritableTrafficSignBlockEntity;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.network.NetworkDirection;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import com.destan.trafficengine.block.WritableTrafficSign;
import com.destan.trafficengine.data.SignTextConfig;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** The text draft stays local until the player closes the editor. */
public class WritableTrafficSignScreen extends TrafficEngineScreen {
    protected final WritableTrafficSignBlockEntity sign;
    protected final TownSignBlock.ETownSignSide side;
    protected final WritableSignConfig config;
    protected final String[] messages;
    protected TownSignVariant variant;
    private boolean saved;
    public WritableTrafficSignScreen(WritableTrafficSignBlockEntity sign) { this(sign, null); }
    protected WritableTrafficSignScreen(WritableTrafficSignBlockEntity sign, TownSignBlock.ETownSignSide side) {
        super(Component.translatable("gui.trafficengine.writable_sign.title"));
        this.sign = sign;
        this.side = side;
        config = side != null && sign instanceof TownSignBlockEntity town ? town.getTownSignRenderConfig(side) : sign.getRenderConfig();
        messages = new String[config.lineData().length];
        for (int i = 0; i < messages.length; i++) messages[i] = side == TownSignBlock.ETownSignSide.BACK && sign instanceof TownSignBlockEntity town ? town.getBackText(i) : sign.getText(i);
        if (side != null) variant = sign.getBlockState().getValue(TownSignBlock.VARIANT);
    }
    @Override protected void init() {
        layoutWindow(440, Math.max(218, 135 + messages.length * 25));
        addCloseButton();
        for (int i = 0; i < messages.length; i++) {
            final int line = i;
            EditBox box = addRenderableWidget(new EditBox(font, left + 166, top + 55 + i * 25, windowWidth - 182, 19, Component.literal(Integer.toString(i + 1))));
            box.setMaxLength(384);
            box.setTextColor(TEColors.TEXT);
            box.setFilter(text -> font.width(text) <= config.lineData()[line].maxLineWidth() * config.scale());
            box.setValue(messages[i] == null ? "" : messages[i]);
            box.setResponder(text -> messages[line] = text);
            if (i == 0) setInitialFocus(box);
        }
        if (side != null) addRenderableWidget(new TEButton(left + 16, top + windowHeight - 56, windowWidth - 32, 20, variant.getValueTranslation(), b -> {
            variant = TownSignVariant.values()[(variant.ordinal() + 1) % TownSignVariant.values().length];
            b.setMessage(variant.getValueTranslation());
        }));
        addRenderableWidget(new TEButton(left + windowWidth - 106, top + windowHeight - 29, 90, 20, CommonComponents.GUI_DONE, b -> onClose()).primary());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderWindow(g);
        renderPreview(g);
        super.render(g, mx, my, tick);
    }
    private void renderPreview(GuiGraphics g) {
        TEPanel.draw(g,left+12,top+49,142,132);
        g.drawString(font,Component.translatable("gui.trafficengine.patterns.preview"),left+20,top+57,TEColors.MUTED,false);
        float scale=84, center=left+83, baseY=top+116;
        var state=sign.getBlockState().getBlock().defaultBlockState();
        if(state.hasProperty(WritableTrafficSign.FACING)) state=state.setValue(WritableTrafficSign.FACING,Direction.NORTH);
        if(variant!=null) state=state.setValue(TownSignBlock.VARIANT,variant);
        g.flush();
        Lighting.setupForFlatItems();
        g.pose().pushPose();
        g.pose().translate(center+scale/2+config.xCenterOffset()*scale,baseY+scale/2,100);
        g.pose().scale(-scale,-scale,-1);
        g.pose().mulPose(Axis.YP.rotationDegrees(config.yRot()));
        minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        g.flush();g.pose().popPose();
        Lighting.setupFor3DItems();
        for(int i=0;i<messages.length;i++) {
            String line=messages[i];if(line==null||line.isEmpty())continue;
            var data=config.lineData()[i];
            float sx=Math.max(data.minScale().x,Math.min(data.maxScale().x,data.maxLineWidth()*scale/font.width(line)));
            float sy=Math.max(data.minScale().y,Math.min(data.maxScale().y,data.maxLineWidth()*scale/font.width(line)));
            g.pose().pushPose();
            g.pose().translate(center+data.xOffset()*scale,baseY+data.yOffset()*scale-SignTextConfig.DEFAULT_LINE_HEIGHT/2F*config.lineData()[0].lineHeightScale()+config.getLineHeightsUntil(i)+config.getLineOffset(i,sy),105);
            g.pose().scale(sx,sy,1);
            g.drawString(font,line,-font.width(line)/2,0,data.color()|0xFF000000,false);
            g.pose().popPose();
        }
    }
    @Override public void onClose() {
        if (!saved) {
            saved = true;
            if (side == null) ModNetworkManager.UPDATE_WRITABLE_SIGN.send(NetworkDirection.toServer(), new WritableSignPacket(sign.getBlockPos(), messages));
            else ModNetworkManager.UPDATE_TOWN_SIGN.send(NetworkDirection.toServer(), new TownSignPacket(sign.getBlockPos(), messages, variant, side));
        }
        super.onClose();
    }
}
