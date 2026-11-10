package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.blueprint.*;
import com.destan.trafficengine.util.ItemData;
import com.destan.trafficengine.client.gui.*;
import com.destan.trafficengine.client.gui.components.*;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** One plan and one main page; detail lists are only built when requested. */
public final class BlueprintScreen extends TrafficEngineScreen {
    private ItemStack tool;
    private CompoundTag data,planToken;
    private BlueprintPlan plan;
    private int rotation,copies,spacing,direction,mode,materialPage;
    private Object identity;
    private List<Map.Entry<Item,Integer>> bill=List.of();
    private int billCopies=-1;
    private boolean materialsPage,thumbnailPage;
    private TEButton previewButton;
    private final com.destan.trafficengine.client.BlueprintThumbnail thumbnail=new com.destan.trafficengine.client.BlueprintThumbnail();

    public BlueprintScreen(ItemStack tool){
        super(text("title"));this.tool=tool;data=BlueprintToolItem.data(tool);
        rotation=data.getInt("Rotation")&3;copies=BlueprintToolItem.copies(data);
        spacing=BlueprintToolItem.spacing(data);direction=net.minecraft.util.Mth.clamp(data.getInt("Direction"),0,5);mode=BlueprintToolItem.mode(data);
    }
    private static Component text(String key,Object...args){return BlueprintToolItem.text(key,args);}
    private void load(){
        CompoundTag raw=data.contains("Plan0")?data.getCompound("Plan0"):null;
        if(raw!=planToken){planToken=raw;plan=raw==null?null:BlueprintPlan.load(raw);billCopies=-1;}
        if(billCopies!=copies){
            bill=plan==null?List.of():new ArrayList<>(plan.materials(copies).entrySet());billCopies=copies;
        }
    }
    private void send(int action){ModNetworkManager.BLUEPRINT.send(NetworkDirection.toServer(),new BlueprintPacket(action,rotation,copies,spacing,direction,mode));}
    private void refresh(){rebuildWidgets();}
    private void localSettings(){
        data.putInt("Rotation",rotation);data.putInt("Copies",copies);data.putInt("Spacing",spacing);data.putInt("Direction",direction);data.putInt("PlacementMode",mode);
        ItemData.set(tool,data);identity=ItemData.identity(tool);load();
        if(previewButton!=null)previewButton.active=plan!=null&&plan.cells().size()*copies<=BlueprintPlan.MAX_TOTAL;
    }
    @Override protected void init(){
        layoutWindow(420,212);load();previewButton=null;
        int x=left+16,w=windowWidth-32,half=(w-8)/2,third=(w-12)/3;
        int footer=top+windowHeight-29;
        if(thumbnailPage){
            thumbnail.update(plan,rotation);
            addRenderableWidget(new PlanButton(x,footer,half,20,text("back"),b->{thumbnailPage=false;refresh();}));
            addRenderableWidget(new PlanButton(x+half+6,footer,w-half-6,20,text("rotation",rotation*90),b->{rotation=(rotation+1)&3;localSettings();refresh();}));
        }else if(materialsPage){
            int rows=Math.max(1,(windowHeight-86)/24),pages=Math.max(1,(bill.size()+rows-1)/rows);materialPage=Math.min(materialPage,pages-1);
            for(int i=0;i<rows&&materialPage*rows+i<bill.size();i++){
                var e=bill.get(materialPage*rows+i);
                var row=addRenderableWidget(new PlanButton(x,top+44+i*24,w,20,e.getKey().getDescription().copy().append(" × "+e.getValue()),b->{}));
                row.setTooltip(Tooltip.create(e.getKey().getDescription().copy().append(" × "+e.getValue())));
            }
            var previous=addRenderableWidget(new PlanButton(x,footer,third,20,Component.literal("←"),b->{materialPage--;refresh();}));previous.active=materialPage>0;
            addRenderableWidget(new PlanButton(x+third+6,footer,third,20,text("back"),b->{materialsPage=false;refresh();}));
            var next=addRenderableWidget(new PlanButton(x+2*(third+6),footer,w-2*(third+6),20,Component.literal("→"),b->{materialPage++;refresh();}));next.active=materialPage+1<pages;
        }else{
            int y=top+46;
            var view=addRenderableWidget(new PlanButton(x,y,half,20,text("plan_preview"),b->{thumbnailPage=true;refresh();}));view.active=plan!=null;
            var materials=addRenderableWidget(new PlanButton(x+half+8,y,w-half-8,20,text("materials_short"),b->{materialsPage=true;materialPage=0;refresh();}));
            materials.active=plan!=null;materials.setTooltip(Tooltip.create(text(mode==0?"materials":"materials_max")));
            y=footer-94;
            addRenderableWidget(new PlanButton(x,y,half,20,text("rotation",rotation*90),b->{rotation=(rotation+1)&3;b.setMessage(text("rotation",rotation*90));localSettings();}));
            addRenderableWidget(new PlanButton(x+half+8,y,w-half-8,20,text("direction",directionText()),b->{direction=(direction+1)%6;b.setMessage(text("direction",directionText()));localSettings();}));
            y+=28;
            var policy=addRenderableWidget(new PlanButton(x,y,w,20,text("mode."+mode),b->{mode=(mode+1)%3;b.setMessage(text("mode."+mode));localSettings();}));
            policy.setTooltip(Tooltip.create(text("mode_hint")));
            y+=28;
            addRenderableWidget(new PlanSlider(x,y,half,0,BlueprintPlan.MAX_SPACING,spacing,n->text("spacing",n),n->{spacing=n;localSettings();}));
            addRenderableWidget(new PlanSlider(x+half+8,y,w-half-8,1,BlueprintPlan.MAX_COPIES,copies,n->text("copies",n),n->{copies=n;localSettings();}));
            int secondary=(w-half-12)/2;
            addRenderableWidget(new PlanButton(x,footer,secondary,20,text("select_mode"),b->{send(0);onClose();}));
            var undo=addRenderableWidget(new PlanButton(x+secondary+6,footer,secondary,20,text("undo"),b->{send(5);onClose();}));undo.setTooltip(Tooltip.create(text("undo_hint")));
            previewButton=addRenderableWidget(new PlanButton(x+w-half,footer,half,20,text("preview"),b->{send(1);onClose();},true));
            previewButton.active=plan!=null&&plan.cells().size()*copies<=BlueprintPlan.MAX_TOTAL;previewButton.setTooltip(Tooltip.create(text("external_notice")));
        }
        addCloseButton();identity=ItemData.identity(tool);
    }
    private Component directionText(){return Component.translatable("gui.trafficengine.blueprint.direction."+BlueprintToolItem.direction(direction).getName());}
    @Override public void tick(){
        if(minecraft.player==null||minecraft.player.getMainHandItem().getItem()!=tool.getItem()&&minecraft.player.getOffhandItem().getItem()!=tool.getItem()){onClose();return;}
        ItemStack held=minecraft.player.getMainHandItem().getItem()==tool.getItem()?minecraft.player.getMainHandItem():minecraft.player.getOffhandItem();
        Object next=ItemData.identity(held);
        if(next!=identity){tool=held;data=BlueprintToolItem.data(held);identity=next;rebuildWidgets();}
    }
    @Override public void onClose(){send(4);super.onClose();}
    @Override public void removed(){thumbnail.close();super.removed();}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        drawFrame(g);super.render(g,mx,my,delta);
        if(thumbnailPage){int h=Math.max(20,windowHeight-91),w=Math.min(windowWidth-32,h*3/2);thumbnail.render(g,left+(windowWidth-w)/2,top+42,w,h);}
    }
    private void drawFrame(GuiGraphics g){
        g.fill(0,0,width,height,TEColors.OVERLAY);
        TEPanel.fillRounded(g,left+2,top+3,windowWidth,windowHeight,0x48000000);
        TEPanel.surface(g,left,top,windowWidth,windowHeight,TEColors.WINDOW,TEColors.SUBTLE_BORDER);
        TEPanel.fillRounded(g,left+10,top+8,23,23,TEColors.ACCENT_SOFT);
        // A small blueprint grid uses the existing GUI buffer, without an image or effects pass.
        for(int i=0;i<3;i++){g.fill(left+16+i*5,top+13,left+17+i*5,top+27,TEColors.ACCENT);g.fill(left+16,top+13+i*5,left+27,top+14+i*5,TEColors.ACCENT);}
        TEText.drawCenteredString(g,font,title,left+windowWidth/2F,TEText.centeredY(g,font,title,top+8,23),TEColors.TEXT);
        if(!thumbnailPage&&!materialsPage){
            TEPanel.fillRounded(g,left+10,top+40,windowWidth-20,32,TEColors.FIELD);
            TEPanel.fillRounded(g,left+10,top+windowHeight-129,windowWidth-20,90,TEColors.FIELD);
        }
    }
    /** Screen-local styling keeps other Traffic Engine menus unchanged. */
    private static final class PlanButton extends TEButton {
        private final boolean primary;
        PlanButton(int x,int y,int w,int h,Component caption,OnPress action){this(x,y,w,h,caption,action,false);}
        PlanButton(int x,int y,int w,int h,Component caption,OnPress action,boolean primary){super(x,y,w,h,caption,action,primary);this.primary=primary;}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){
            boolean hover=active&&isHoveredOrFocused();
            int fill=!active?TEColors.WINDOW:primary?TEColors.ACCENT:hover?TEColors.HOVER:TEColors.CONTROL;
            TEPanel.fillRounded(g,getX(),getY(),width,height,fill);
            if(hover)TEPanel.outline(g,getX(),getY(),width,height,primary?TEColors.ACCENT:TEColors.SELECTION_BORDER);
            renderLabel(g,!active?TEColors.MUTED:primary?TEColors.INK:TEColors.TEXT);
        }
    }
    private static final class PlanSlider extends TEIntegerSlider {
        private Component source;
        private MenuTextBounds.Bounds bounds;
        private int fittedWidth;
        private float fittedScale;
        private String fitted="";
        PlanSlider(int x,int y,int w,int min,int max,int initial,java.util.function.IntFunction<Component> caption,java.util.function.IntConsumer change){super(x,y,w,min,max,1,initial,caption,change);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){
            var font=Minecraft.getInstance().font;var next=MenuTextBounds.of(font,getMessage());float scale=TEText.layoutScale(g);
            if(source!=getMessage()||bounds!=next||fittedWidth!=width||fittedScale!=scale){
                source=getMessage();bounds=next;fittedWidth=width;fittedScale=scale;fitted=font.plainSubstrByWidth(source.getString(),Math.max(1,(int)((width-12)/scale)));
            }
            TEText.drawCenteredString(g,font,fitted,getX()+width/2F,TEText.centeredY(g,font,fitted,getY(),14),active?TEColors.TEXT:TEColors.MUTED);
            int start=getX()+6,end=getX()+width-6,knob=start+(int)Math.round((end-start)*value),y=getY()+17;
            g.fill(start,y,end,y+2,TEColors.SUBTLE_BORDER);g.fill(start,y,knob,y+2,TEColors.ACCENT);
            TEPanel.fillRounded(g,knob-3,y-2,6,6,isHoveredOrFocused()?TEColors.SELECTION_BORDER:TEColors.ACCENT);
        }
    }
}
