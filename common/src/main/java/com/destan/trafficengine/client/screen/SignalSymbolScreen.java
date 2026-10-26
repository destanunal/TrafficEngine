package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.PixelPattern;
import com.destan.trafficengine.data.SignalSymbols;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Edits one immutable slot override; the parent is updated only on Done. */
public final class SignalSymbolScreen extends TrafficEngineScreen {
    private final Screen parent;
    private final int slot;
    private final SignalSymbols original;
    private final Consumer<SignalSymbols> save;
    private TrafficLightIcon selected;
    private PixelPattern mask;
    public SignalSymbolScreen(Screen parent,int slot,SignalSymbols symbols,Consumer<SignalSymbols> save) {
        super(Component.translatable("gui.trafficengine.symbols.signal",slot+1));
        this.parent=parent;this.slot=slot;this.original=symbols;this.save=save;
        selected=symbols.inherits(slot)?null:symbols.icon(slot,TrafficLightIcon.NONE);mask=symbols.mask(slot,PixelPattern.EMPTY);
    }
    private void refresh(){clearWidgets();init();}
    @Override protected void init() {
        layoutWindow(332,230);addCloseButton();
        TrafficLightIcon[] icons=TrafficLightIcon.values();
        for(int i=0;i<icons.length;i++) {
            TrafficLightIcon icon=icons[i];int x=left+16+i%4*31,y=top+58+i/4*31;
            TEButton button=addRenderableWidget(new TEButton(x,y,27,27,icon.getValueTranslation(),b->{selected=icon;mask=PixelPattern.EMPTY;refresh();}){
                @Override protected void renderLabel(GuiGraphics g,int color){TESignalIcons.draw(g,icon,getX()+5,getY()+5,color);}
            });
            button.selected(selected==icon&&mask.isEmpty());button.setTooltip(Tooltip.create(icon.getValueTranslation()));
        }
        addRenderableWidget(new TEButton(left+16,top+128,146,22,Component.translatable("gui.trafficengine.stencil.add_icon"),b->
            minecraft.setScreen(new PixelPatternScreen(this,mask,value->{selected=TrafficLightIcon.NONE;mask=value;}))));
        addRenderableWidget(new TEButton(left+16,top+157,windowWidth-32,22,Component.translatable("gui.trafficengine.symbols.inherit"),b->{selected=null;mask=PixelPattern.EMPTY;refresh();})).selected(selected==null);
        addRenderableWidget(new TEButton(left+16,top+windowHeight-32,90,20,CommonComponents.GUI_CANCEL,b->onClose()));
        addRenderableWidget(new TEButton(left+windowWidth-106,top+windowHeight-32,90,20,CommonComponents.GUI_DONE,b->{save.accept(original.with(slot,selected,mask));onClose();}).primary());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int x,int y,float tick) {
        renderWindow(g);int px=left+windowWidth-102,py=top+55;
        TEPanel.surface(g,px,py,78,78,TEColors.FIELD,TEColors.SUBTLE_BORDER);
        if(selected!=null) {
            if(!mask.isEmpty())for(var r:mask.rectangles())g.fill(px+7+r.x()*4,py+7+r.y()*4,px+7+(r.x()+r.width())*4,py+7+(r.y()+r.height())*4,TEColors.ACCENT);
            else {g.pose().pushPose();g.pose().translate(px+15,py+15,0);g.pose().scale(3,3,1);TESignalIcons.draw(g,selected,0,0,TEColors.ACCENT);g.pose().popPose();}
        }
        super.render(g,x,y,tick);
    }
}
