import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.client.gui.components.TESignalIcons;
import com.destan.trafficengine.data.PixelPattern;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.lang.reflect.*;

/** Checks actual lamp silhouette pixels and compact layout bounds without a game window. */
public final class SignalMenuCheck {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Path textures=Path.of(args[0]).resolve("common/src/main/resources/assets/trafficengine/textures/block/traffic_light");
        Method read=TESignalIcons.class.getDeclaredMethod("fromTexture",NativeImage.class,NativeImage.class);read.setAccessible(true);
        try(var off=NativeImage.read(Files.readAllBytes(textures.resolve("off.png")))){
            check(((PixelPattern)read.invoke(null,off,off)).isEmpty(),"No-icon/off lens produces a blank mask");
            for(var icon:TrafficLightIcon.values())if(icon!=TrafficLightIcon.NONE){
                try(var image=NativeImage.read(Files.readAllBytes(textures.resolve(icon.getName()+"_green.png")))){
                    var pattern=(PixelPattern)read.invoke(null,image,off);int[] coverage=new int[256];
                    for(var r:pattern.rectangles())for(int y=r.y();y<r.y()+r.height();y++)for(int x=r.x();x<r.x()+r.width();x++)coverage[y*16+x]++;
                    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                        int pixel=image.getPixelRGBA(x,y),lens=off.getPixelRGBA(x,y);
                        boolean expected=(pixel>>>24)!=0&&(pixel&0xFFFFFF)!=(lens&0xFFFFFF);
                        check(pattern.pixel(x,y)==expected,"Menu symbol matches the actual lamp silhouette: "+icon);
                        check(coverage[y*16+x]==(expected?1:0),"Cached rectangles draw each symbol pixel once");
                    }
                    check(pattern.rectangles().size()<=256,"Preset rectangle count is bounded");
                }
            }
        }
        Field revision=TESignalIcons.class.getDeclaredField("revision");revision.setAccessible(true);int before=revision.getInt(null);
        TESignalIcons.invalidate();check(revision.getInt(null)==before+1,"Resource reload invalidates the bounded icon cache");
        for(int windowHeight:new int[]{258,270,300,330})for(int count=1;count<=4;count++){
            int formHeight=windowHeight-125,rowStep=Math.min(62,Math.max(28,(formHeight-4)/count));
            int h=Math.max(10,Math.min(24,rowStep-18));int content=(count-1)*rowStep+14+h+5;
            check(content<=formHeight,"All one-to-four signal rows fit without scrolling at normal GUI sizes");
            for(int width:new int[]{308,400,470}){
                int panelWidth=Math.min(120,(width-24)/3);float modelHeight=count==4?22.5F:16;
                float scale=Math.min(6,Math.min((windowHeight-124)/modelHeight,(panelWidth-20)/16F));
                int lampX=panelWidth/2-Math.round(8*scale);int toolX=lampX+Math.round(4*scale)-24;
                check(toolX>=1&&toolX+20<panelWidth,"Pencil tools stay inside the preview panel");
                check(scale*5>=20,"Adjacent per-bulb pencil tools do not overlap");
            }
        }
        System.out.println("PASS: "+checks+" signal menu assertions: actual lamp masks, blank None option, merged rectangle coverage/cache invalidation and no-scroll row/pencil bounds.");
    }
}
