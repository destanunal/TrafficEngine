import com.destan.trafficengine.block.PedestrianRailingBlock;
import com.destan.trafficengine.client.PedestrianRailingParticles;
import com.destan.trafficengine.client.gui.MenuGlyphInk;
import com.destan.trafficengine.client.gui.MenuTextBounds;
import com.destan.trafficengine.mixin.MenuFontAccessor;
import com.google.gson.*;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.core.*;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Headless regression against pixels extracted from the installed vanilla font assets. */
public final class MenuPolishCheck {
    static int checks,scans;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static void near(float a,float b,String why){check(Math.abs(a-b)<.0001,why+": "+a+" / "+b);}
    static final class Glyph implements GlyphInfo,MenuGlyphInk,AutoCloseable {
        final NativeImage image;final float scale,advance;final int ascent;final Ink expected;
        Glyph(JsonObject g)throws IOException{
            image=NativeImage.read(Files.readAllBytes(Path.of(g.get("file").getAsString())));
            scale=g.get("scale").getAsFloat();advance=g.get("advance").getAsFloat();ascent=g.get("ascent").getAsInt();
            expected=new Ink(g.get("expectedTop").getAsFloat(),g.get("expectedBottom").getAsFloat());
        }
        public float getAdvance(){return advance;}
        public BakedGlyph bake(Function<SheetGlyphInfo,BakedGlyph> baker){throw new AssertionError("No atlas bake for bitmap ink measurements");}
        public Ink trafficengine$ink(){scans++;return MenuGlyphInk.bitmap(image,0,0,image.getWidth(),image.getHeight(),scale,ascent);}
        public void close(){image.close();}
    }
    static final class GlyphSet extends FontSet {
        final Map<Integer,Glyph> glyphs;GlyphSet(Map<Integer,Glyph> data){super(null,Style.DEFAULT_FONT);glyphs=data;}
        @Override public GlyphInfo getGlyphInfo(int c,boolean filter){return Objects.requireNonNull(glyphs.get(c),"Missing glyph "+c);}
        @Override public BakedGlyph getGlyph(int c){throw new AssertionError("No per-frame glyph bake");}
    }
    static final class CaptionFont extends Font implements MenuFontAccessor {
        final GlyphSet glyphs;int widths;
        CaptionFont(GlyphSet data){super(key->data,false);glyphs=data;}
        public FontSet trafficengine$fontSet(ResourceLocation ignored){return glyphs;}
        @Override public int width(FormattedCharSequence chars){widths++;float[] n={0};chars.accept((i,s,c)->{n[0]+=Character.isWhitespace(c)?4:glyphs.glyphs.get(c).advance;return true;});return (int)Math.ceil(n[0]);}
    }
    static final class ReloadLanguage extends Language {
        final Language delegate;ReloadLanguage(Language original){delegate=original;}
        public String getOrDefault(String key,String fallback){return delegate.getOrDefault(key,fallback);}
        public boolean has(String key){return delegate.has(key);}
        public boolean isDefaultRightToLeft(){return delegate.isDefaultRightToLeft();}
        public FormattedCharSequence getVisualOrder(FormattedText text){return delegate.getVisualOrder(text);}
    }
    static void fonts(Path directory)throws Exception{
        Map<Integer,Glyph> glyphs=new HashMap<>();
        var data=JsonParser.parseString(Files.readString(directory.resolve("glyphs.json"))).getAsJsonArray();
        try{
            for(var element:data){var g=element.getAsJsonObject();Glyph glyph=new Glyph(g);glyphs.put(g.get("code").getAsInt(),glyph);var actual=glyph.trafficengine$ink();near(actual.top(),glyph.expected.top(),"Source pixel top");near(actual.bottom(),glyph.expected.bottom(),"Source pixel bottom");}
            var font=new CaptionFont(new GlyphSet(glyphs));
            var labels=JsonParser.parseString(Files.readString(directory.resolve("labels.json"))).getAsJsonArray();
            for(var element:labels){String caption=element.getAsJsonArray().get(1).getAsString();var bounds=MenuTextBounds.of(font,caption);
                float[] expected={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};caption.codePoints().filter(c->!Character.isWhitespace(c)).forEach(c->{var g=glyphs.get(c);expected[0]=Math.min(expected[0],g.expected.top());expected[1]=Math.max(expected[1],g.expected.bottom());});
                near(bounds.top(),expected[0],caption+" top");near(bounds.bottom(),expected[1],caption+" bottom");
                int scansBefore=scans,widths=font.widths;
                for(int frame=0;frame<120;frame++)check(MenuTextBounds.of(font,caption)==bounds,"Cached caption each frame");
                check(scans==scansBefore&&font.widths==widths,"No repeated source pixel scans or width measurements");
                for(float windowScale:new float[]{.75F,.9F,.94F,1F})for(int guiScale:new int[]{1,2,3,4})for(int height:new int[]{19,20,22,26}){
                    float pixelScale=Math.max(1,Math.round(windowScale*guiScale));float layout=pixelScale/(windowScale*guiScale);
                    float y=MenuTextBounds.centeredExact(7,height,layout,bounds);
                    float origin=(float)Math.floor((3.25+y*windowScale)*guiScale+.5);
                    double actualCenter=origin+(bounds.top()+bounds.bottom())*.5*pixelScale;
                    double expectedCenter=(3.25+(7+height*.5)*windowScale)*guiScale;
                    check(Math.abs(actualCenter-expectedCenter)<=.501,"One final pixel snap keeps localized ink centered: "+caption);
                }
            }
            String caption="Boyama Ayarları";var first=MenuTextBounds.of(font,caption);
            MenuTextBounds.invalidate();check(MenuTextBounds.of(font,caption)!=first,"Resource reload invalidates caption cache");
            var before=MenuTextBounds.of(font,caption);Language original=Language.getInstance();
            try{Language.inject(new ReloadLanguage(original));check(MenuTextBounds.of(font,caption)!=before,"Language replacement invalidates caption cache");}finally{Language.inject(original);}
            for(int i=0;i<1100;i++)MenuTextBounds.of(font,new String("All"));
            Field cache=MenuTextBounds.class.getDeclaredField("CACHE");cache.setAccessible(true);check(((Map<?,?>)cache.get(null)).size()<=1024,"Caption cache stays bounded");
            Field ink=MenuTextBounds.class.getDeclaredField("GLYPHS");ink.setAccessible(true);check(((Map<?,?>)ink.get(null)).size()<=4096,"Glyph cache stays bounded");
            int[] rows=new int[16];rows[2]=rows[3]=rows[11]=1;
            var unicode=MenuGlyphInk.unihex(new UnihexProvider.LineData(){public int line(int row){return rows[row];}public int bitWidth(){return 8;}});
            near(unicode.top(),1,"Unihex source top");near(unicode.bottom(),6,"Unihex source bottom");
            try(var blank=new NativeImage(8,8,true)){for(int y=0;y<8;y++)for(int x=0;x<8;x++)blank.setPixelRGBA(x,y,0);var empty=MenuGlyphInk.bitmap(blank,0,0,8,8,1,7);check(empty.top()==empty.bottom(),"Empty source glyph has no ink");}
        }finally{for(var glyph:glyphs.values())glyph.close();}
    }
    static void railing()throws Exception{
        PedestrianRailingBlock rail=(PedestrianRailingBlock)Update20RegressionCheck.equipment()[2];
        var state=rail.defaultBlockState();check(state.getValue(PedestrianRailingBlock.GRAY),"Gray placement default retained");
        var up=rail.updateShape(state,Direction.UP,state,null,BlockPos.ZERO,BlockPos.ZERO.above());
        var down=rail.updateShape(state,Direction.DOWN,state,null,BlockPos.ZERO,BlockPos.ZERO.below());
        check(up.getValue(PedestrianRailingBlock.UP)&&!up.getValue(PedestrianRailingBlock.DOWN),"Upper placement joins lower post");
        check(down.getValue(PedestrianRailingBlock.DOWN)&&!down.getValue(PedestrianRailingBlock.UP),"Lower neighbor joins upper pickets");
        check(!rail.updateShape(up,Direction.UP,Blocks.AIR.defaultBlockState(),null,BlockPos.ZERO,BlockPos.ZERO.above()).getValue(PedestrianRailingBlock.UP),"Upper removal restores cap");
        check(!rail.updateShape(down,Direction.DOWN,Blocks.AIR.defaultBlockState(),null,BlockPos.ZERO,BlockPos.ZERO.below()).getValue(PedestrianRailingBlock.DOWN),"Lower removal restores bottom rail");
        for(var s:rail.getStateDefinition().getPossibleStates()){
            var normal=s.setValue(PedestrianRailingBlock.GRAY,true).setValue(PedestrianRailingBlock.UP,false).setValue(PedestrianRailingBlock.DOWN,false);
            check(s.getShape(null,BlockPos.ZERO,CollisionContext.empty())==normal.getShape(null,BlockPos.ZERO,CollisionContext.empty()),"Colors and vertical joins reuse fence outline cache");
            check(s.getCollisionShape(null,BlockPos.ZERO,CollisionContext.empty())==normal.getCollisionShape(null,BlockPos.ZERO,CollisionContext.empty()),"Colors and vertical joins reuse fence collision cache");
            var texture=PedestrianRailingParticles.texture(s);check(texture.getPath().equals(s.getValue(PedestrianRailingBlock.GRAY)?"block/sign":"block/pedestrian_railing_charcoal"),"Particle sprite follows actual state color");
            check(texture==PedestrianRailingParticles.texture(s),"No particle texture identifier allocation");
        }
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Update20RegressionCheck.headlessIcons();
        fonts(Path.of(args[0]));railing();
        System.out.println("PASS: "+checks+" assertions for real vanilla glyph pixels, cached centering across 36 localized labels/scales, language reloads, vertical railing joins, cached shapes and state-colored particle selection.");
    }
}
