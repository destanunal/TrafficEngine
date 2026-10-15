package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import com.destan.trafficengine.client.ber.RoadSignGraphics;
import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEEditBox;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.RoadSignDesign;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.packets.cts.RoadInformationSignPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** A local draft with direct choices and the same live preview as the placed sign. */
public class RoadInformationSignScreen extends ScaledTrafficEngineScreen {
    private static final String KEY="gui.trafficengine.road_sign.";
    private static final String[] ARROWS={"—","↑","←","→","↖","↗","↓"};
    private final RoadInformationSignBlockEntity sign;
    private final String[] lines=new String[3];
    private final String[] distances=new String[3];
    private final EditBox[] distanceFields=new EditBox[3];
    private final int[] arrows=new int[3];
    private final EditBox[] fields=new EditBox[3];
    private final TEButton[] panelButtons=new TEButton[3];
    private final List<TEButton> panelChoices=new ArrayList<>();
    private int panelMenuRow=-1,panelMenuX,panelMenuY;
    private static final int PANEL_MENU_WIDTH=100,PANEL_MENU_HEIGHT=108;
    private EditBox speedInput;
    private RoadSignDesign design;
    private int theme,speed,tab,selectedRow;
    private boolean enabled,saved,discard;
    private int previewX,previewWidth;
    private float previewLeft,previewTop,previewScale;

    public RoadInformationSignScreen(RoadInformationSignBlockEntity sign) {
        super(Component.translatable(KEY+(sign.kind()==Kind.VARIABLE_SPEED?"speed_title":sign.kind()==Kind.HIGHWAY?"large_title":"title")));
        this.sign=sign;
        for(int i=0;i<3;i++) {lines[i]=sign.destination(i);arrows[i]=sign.arrow(i);distances[i]=sign.distance(i);}
        theme=sign.theme();speed=sign.speed();enabled=sign.enabled();design=sign.design();
    }
    private Component text(String suffix) {return Component.translatable(KEY+suffix);}
    private void choice(int x,int y,int width,Component caption,boolean selected,Runnable action) {
        addRenderableWidget(new TEButton(x,y,width,22,caption,b->{action.run();refreshEditor();}).selected(selected));
    }
    private void label(GuiGraphics g,String key,int y) {g.drawString(font,text(key),left+20,top+y,TEColors.MUTED,false);}
    private void edit(int x,int y,int width,String value,int max,Component caption,java.util.function.Consumer<String> respond,int row) {
        EditBox box=addRenderableWidget(new TEEditBox(font,x,y,width,20,caption));
        box.setMaxLength(max);box.setValue(value);box.setResponder(respond);
        if(row>=0)fields[row]=box;
    }
    private void updateDesign(int rows,int alignment,int size,int ink,int frame,boolean bold,boolean leftArrows,boolean dividers) {
        design=new RoadSignDesign(rows,alignment,size,ink,frame,bold,leftArrows,dividers,design.panelColors());
    }

    @Override protected void initEditor() {
        layoutWindow(592,336);addCloseButton();
        previewX=left+304;previewWidth=windowWidth-320;
        java.util.Arrays.fill(fields,null);
        java.util.Arrays.fill(distanceFields,null);
        java.util.Arrays.fill(panelButtons,null);
        panelChoices.clear();panelMenuRow=-1;
        speedInput=null;
        if(sign.kind()==Kind.VARIABLE_SPEED) initSpeed();
        else {
            for(int i=0;i<3;i++) {
                final int selected=i;
                choice(left+16+i*90,top+44,84,text("tab."+i),tab==i,()->tab=selected);
            }
            if(tab==0)initContent();else if(tab==1)initAppearance();else initLayout();
        }
        addRenderableWidget(new TEButton(left+16,top+windowHeight-30,90,22,text("discard"),b->{discard=true;onClose();}));
        addRenderableWidget(new TEButton(left+windowWidth-106,top+windowHeight-30,90,22,text("save"),b->onClose()).primary());
    }

    private void initSpeed() {
        EditBox number=addRenderableWidget(new TEEditBox(font,left+20,top+96,252,26,text("speed")));
        speedInput=number;
        number.setMaxLength(3);number.setFilter(s->s.matches("[0-9]{0,3}"));number.setValue(Integer.toString(speed));
        number.setResponder(s->{if(!s.isEmpty())speed=Math.max(5,Math.min(200,Integer.parseInt(s)));});
        int[] limits={30,50,70,90,110,130};
        for(int i=0;i<limits.length;i++) {
            final int value=limits[i];
            choice(left+20+i%3*86,top+153+i/3*28,80,Component.literal(Integer.toString(value)),speed==value,()->speed=value);
        }
        choice(left+20,top+233,252,text(enabled?"enabled":"disabled"),enabled,()->enabled=!enabled);
        setInitialFocus(number);
    }

    private void initContent() {
        int count=design.visibleRows(sign.kind()==Kind.HIGHWAY);
        for(int i=0;i<count;i++) {
            final int row=i;
            edit(left+20,top+92+i*70,164,lines[i],96,Component.translatable(KEY+"destination",i+1),value->lines[row]=value,i);
            EditBox distance=addRenderableWidget(new TEEditBox(font,left+190,top+92+i*70,32,20,text("distance")));
            distance.setMaxLength(12);distance.setValue(distances[i]);
            distance.setHint(Component.literal("km"));
            distance.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("distance")));
            distance.setResponder(value->distances[row]=value);distanceFields[i]=distance;
            panelButtons[i]=addRenderableWidget(new TEButton(left+228,top+92+i*70,44,22,text("panel"),
                    b->togglePanelMenu(row)).selected(design.panelColor(i)!=RoadSignDesign.NO_PANEL));
            for(int j=0;j<ARROWS.length;j++) {
                final int arrow=j;
                if (j==4 || j==5) {
                    addRenderableWidget(new TEButton(left+20+j*36,top+120+i*70,30,22,Component.literal(ARROWS[j]),
                            b->{arrows[row]=arrow;selectedRow=row;refreshEditor();}) {
                        @Override protected void renderLabel(GuiGraphics g,int color) {
                            g.pose().pushPose();
                            g.pose().translate(getX()+getWidth()/2F,getY()+getHeight()/2F,0);
                            g.pose().scale(1.45F,1.45F,1);
                            g.drawString(font,getMessage(),-font.width(getMessage())/2,-font.lineHeight/2,color,false);
                            g.pose().popPose();
                        }
                    }.selected(arrows[i]==j));
                } else {
                    choice(left+20+j*36,top+120+i*70,30,Component.literal(ARROWS[j]),arrows[i]==j,()->{arrows[row]=arrow;selectedRow=row;});
                }
            }
        }
        setInitialFocus(fields[Math.min(selectedRow,count-1)]);
    }

    private Component panelCaption(int color) {
        return color==RoadSignDesign.NO_PANEL?text("panel.none"):text("theme."+switch(color) {
            case RoadSignDesign.WHITE_PANEL -> 3;
            case RoadSignDesign.BLUE_PANEL -> 1;
            case RoadSignDesign.BROWN_PANEL -> 2;
            default -> 0;
        });
    }
    private void closePanelMenu() {
        int row=panelMenuRow;
        for(TEButton button:panelChoices)removeWidget(button);
        panelChoices.clear();panelMenuRow=-1;
        if(row>=0 && panelButtons[row]!=null)setFocused(panelButtons[row]);
    }
    private void togglePanelMenu(int row) {
        boolean closing=panelMenuRow==row;
        closePanelMenu();
        if(closing)return;
        panelMenuRow=row;selectedRow=row;
        TEButton anchor=panelButtons[row];
        panelMenuX=anchor.getX()+anchor.getWidth()-PANEL_MENU_WIDTH;
        panelMenuY=anchor.getY()+anchor.getHeight()+3;
        if(panelMenuY+PANEL_MENU_HEIGHT>top+298)panelMenuY=anchor.getY()-PANEL_MENU_HEIGHT-3;
        for(int color=RoadSignDesign.NO_PANEL;color<=RoadSignDesign.GREEN_PANEL;color++) {
            final int value=color;
            TEButton option=addRenderableWidget(new TEButton(panelMenuX+4,panelMenuY+4+color*20,
                    PANEL_MENU_WIDTH-8,20,panelCaption(color),b->{
                        design=design.withPanelColor(row,value);refreshEditor();setFocused(panelButtons[row]);
                    }) {
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float tick) {
                    g.pose().pushPose();g.pose().translate(0,0,301);
                    super.renderWidget(g,mx,my,tick);g.pose().popPose();
                }
                @Override protected void renderLabel(GuiGraphics g,int foreground) {
                    if(value==RoadSignDesign.NO_PANEL)g.fill(getX()+6,getY()+9,getX()+14,getY()+11,foreground);
                    else g.fill(getX()+6,getY()+6,getX()+14,getY()+14,RoadSignGraphics.panelColor(value));
                    g.drawString(font,getMessage(),getX()+20,getY()+6,foreground,false);
                }
            }.selected(design.panelColor(row)==color));
            panelChoices.add(option);
        }
        setFocused(panelChoices.get(design.panelColor(row)));
    }

    private void colorChoice(int x,int y,int width,String key,int color,boolean selected,Runnable action) {
        addRenderableWidget(new TEButton(x,y,width,22,text(key),b->{action.run();refreshEditor();}) {
            @Override protected void renderLabel(GuiGraphics g,int foreground) {
                g.fill(getX()+6,getY()+6,getX()+14,getY()+16,color);
                g.drawString(font,getMessage(),getX()+20,getY()+7,foreground,false);
            }
        }.selected(selected));
    }
    private void initAppearance() {
        int[] colors={0xFF176B48,0xFF174C9B,0xFF784A2D,0xFFF2F4EF};
        // Two rows keep color names readable instead of squeezing four long labels together.
        for(int i=0;i<4;i++) {
            final int value=i;
            colorChoice(left+20+i%2*130,top+96+i/2*26,122,"theme."+i,colors[i],theme==i,()->theme=value);
        }
        for(int i=0;i<4;i++) {
            final int value=i;
            choice(left+20+i*65,top+183,59,text("ink."+i),design.ink()==i,()->updateDesign(design.rows(),design.alignment(),design.textSize(),value,design.frame(),design.bold(),design.leftArrows(),design.dividers()));
        }
        for(int i=0;i<3;i++) {
            final int value=i;
            choice(left+20+i*86,top+225,80,text("frame."+i),design.frame()==i,()->updateDesign(design.rows(),design.alignment(),design.textSize(),design.ink(),value,design.bold(),design.leftArrows(),design.dividers()));
        }
        choice(left+20,top+267,122,text(design.bold()?"bold.on":"bold.off"),design.bold(),()->updateDesign(design.rows(),design.alignment(),design.textSize(),design.ink(),design.frame(),!design.bold(),design.leftArrows(),design.dividers()));
        choice(left+150,top+267,122,text(design.dividers()?"dividers.on":"dividers.off"),design.dividers(),()->updateDesign(design.rows(),design.alignment(),design.textSize(),design.ink(),design.frame(),design.bold(),design.leftArrows(),!design.dividers()));
    }

    private void initLayout() {
        for(int i=0;i<3;i++) {
            final int preset=i;
            choice(left+20+i*86,top+96,80,text("template."+i),false,()->{
                if(preset==0)design=RoadSignDesign.DEFAULT;
                else if(preset==1)design=new RoadSignDesign(3,1,110,0,1,true,false,true);
                else design=new RoadSignDesign(2,0,125,0,1,false,true,false);
            });
        }
        int count=sign.kind()==Kind.HIGHWAY?3:2;
        for(int i=1;i<=count;i++) {
            final int value=i;
            choice(left+20+(i-1)*86,top+141,80,Component.translatable(KEY+"rows.value",i),design.visibleRows(sign.kind()==Kind.HIGHWAY)==i,()->updateDesign(value,design.alignment(),design.textSize(),design.ink(),design.frame(),design.bold(),design.leftArrows(),design.dividers()));
        }
        for(int i=0;i<3;i++) {
            final int value=i;
            choice(left+20+i*86,top+186,80,text("align."+i),design.alignment()==i,()->updateDesign(design.rows(),value,design.textSize(),design.ink(),design.frame(),design.bold(),design.leftArrows(),design.dividers()));
        }
        choice(left+20,top+231,34,Component.literal("−"),false,()->updateDesign(design.rows(),design.alignment(),design.textSize()-5,design.ink(),design.frame(),design.bold(),design.leftArrows(),design.dividers()));
        addRenderableWidget(new TEButton(left+60,top+231,172,22,Component.literal(design.textSize()+"%"),b->{})).active=false;
        choice(left+238,top+231,34,Component.literal("+"),false,()->updateDesign(design.rows(),design.alignment(),design.textSize()+5,design.ink(),design.frame(),design.bold(),design.leftArrows(),design.dividers()));
        choice(left+20,top+273,122,text("arrows.left"),design.leftArrows(),()->updateDesign(design.rows(),design.alignment(),design.textSize(),design.ink(),design.frame(),design.bold(),true,design.dividers()));
        choice(left+150,top+273,122,text("arrows.right"),!design.leftArrows(),()->updateDesign(design.rows(),design.alignment(),design.textSize(),design.ink(),design.frame(),design.bold(),false,design.dividers()));
    }

    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        beginWindow(g);
        TEPanel.draw(g,left+12,top+74,276,228);
        TEPanel.draw(g,previewX,top+74,previewWidth,228);
        if(sign.kind()==Kind.VARIABLE_SPEED) {
            label(g,"speed",82);label(g,"speed.presets",139);label(g,"screen.title",219);
        } else if(tab==0) {
            int count=design.visibleRows(sign.kind()==Kind.HIGHWAY);
            for(int i=0;i<count;i++)g.drawString(font,Component.translatable(KEY+"destination",i+1),left+20,top+79+i*70,TEColors.MUTED,false);
        } else if(tab==1) {
            label(g,"background",82);label(g,"ink.title",168);label(g,"frame.title",211);label(g,"details",253);
        } else {
            label(g,"template.title",82);label(g,"rows.title",127);label(g,"align.title",172);label(g,"size.title",217);label(g,"arrows.title",259);
        }
        g.drawString(font,text("live_preview"),previewX+12,top+83,TEColors.ACCENT,false);
        g.flush();g.pose().pushPose();
        previewScale=Math.min((previewWidth-24F)/RoadSignGraphics.width(sign.kind()),194F/RoadSignGraphics.height(sign.kind()));
        previewLeft=previewX+(previewWidth-RoadSignGraphics.width(sign.kind())*previewScale)/2;
        previewTop=top+99+(194-RoadSignGraphics.height(sign.kind())*previewScale)/2;
        g.pose().translate(previewLeft,previewTop,150);g.pose().scale(previewScale,previewScale,1);
        new RoadSignGraphics(g.pose(),g.bufferSource(),font,LightTexture.FULL_BRIGHT).draw(sign.kind(),lines,arrows,distances,"",theme,speed,enabled,design);
        g.flush();g.pose().popPose();
        if(panelMenuRow>=0) {
            g.pose().pushPose();g.pose().translate(0,0,300);
            TEPanel.draw(g,panelMenuX,panelMenuY,PANEL_MENU_WIDTH,PANEL_MENU_HEIGHT);
            g.pose().popPose();
        }
        finishWindow(g,mx,my,tick);
    }

    @Override public boolean mouseClicked(double x,double y,int button) {
        if(panelMenuRow>=0) {
            double px=editorCoordinate(x),py=editorCoordinate(y);
            for(TEButton choice:panelChoices)if(choice.isMouseOver(px,py))return choice.mouseClicked(px,py,button);
            closePanelMenu();return true;
        }
        if(button==0 && sign.kind()!=Kind.VARIABLE_SPEED && previewScale>0) {
            double px=(editorCoordinate(x)-previewLeft)/previewScale,py=(editorCoordinate(y)-previewTop)/previewScale;
            float first=12;
            int count=design.visibleRows(sign.kind()==Kind.HIGHWAY);
            if(px>=0 && px<RoadSignGraphics.width(sign.kind()) && py>=first && py<RoadSignGraphics.height(sign.kind())-10) {
                selectedRow=Math.min(count-1,(int)((py-first)/((RoadSignGraphics.height(sign.kind())-first-10)/count)));
                tab=0;refreshEditor();setFocused(fields[selectedRow]);return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        if(panelMenuRow>=0) {
            if(key==GLFW.GLFW_KEY_ESCAPE) {closePanelMenu();return true;}
            if(key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_DOWN || key==GLFW.GLFW_KEY_TAB) {
                int index=panelChoices.indexOf(getFocused());
                int direction=key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_TAB && hasShiftDown()?-1:1;
                setFocused(panelChoices.get(Math.floorMod(index+direction,panelChoices.size())));return true;
            }
            if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER || key==GLFW.GLFW_KEY_SPACE) {
                int index=panelChoices.indexOf(getFocused());
                if(index>=0)panelChoices.get(index).onPress();return true;
            }
            return true;
        }
        return super.keyPressed(key,scanCode,modifiers);
    }
    @Override public void tick() {
        for(EditBox field:fields)if(field!=null)field.tick();
        for(EditBox field:distanceFields)if(field!=null)field.tick();
        if(speedInput!=null)speedInput.tick();
    }
    @Override public void onClose() {
        if(!saved && !discard) {
            saved=true;
            ModNetworkManager.UPDATE_ROAD_INFORMATION_SIGN.send(NetworkDirection.toServer(),new RoadInformationSignPacket(sign.getBlockPos(),lines,arrows,distances,"",theme,speed,enabled,design));
        }
        super.onClose();
    }
}
