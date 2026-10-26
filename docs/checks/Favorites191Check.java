import com.destan.trafficengine.client.gui.PatternCatalog;
import com.destan.trafficengine.client.gui.PatternFavorites;
import com.destan.trafficengine.data.PixelPattern;
import java.nio.file.*;
import java.util.*;
public final class Favorites191Check {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static PixelPattern p(int i){return PixelPattern.of(new long[]{i,0,0,0});}
    static void reload() throws Exception {
        var loaded=PatternFavorites.class.getDeclaredField("loaded"); loaded.setAccessible(true); loaded.setBoolean(null,false);
        var custom=PatternFavorites.class.getDeclaredField("CUSTOM");custom.setAccessible(true);((List<?>)custom.get(null)).clear();
        var presets=PatternFavorites.class.getDeclaredField("FAVORITES");presets.setAccessible(true);((BitSet)presets.get(null)).clear();
    }
    public static void main(String[] args) throws Exception {
        Path file=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config/trafficengine-patterns.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file,"favorites=29,30,31\nlast_category=FAVORITES\ncustom.0=1:0:0:0\ncustom.1=1:0:0:0\ncustom.2=bogus\n");
        check(PatternFavorites.contains(10)&&PatternFavorites.contains(11)&&PatternFavorites.contains(31),"Removed preset aliases remain favorites");
        check(PatternFavorites.getLastCategory()==PatternCatalog.Category.FAVORITES,"Category preserved");
        check(PatternFavorites.customPatterns().size()==1,"Invalid and duplicate custom entries rejected");
        for(int i=2;i<=128;i++)PatternFavorites.toggle(p(i));
        check(PatternFavorites.customPatterns().size()==128&&!PatternFavorites.canAddCustom(),"Custom favorite cap");
        PatternFavorites.toggle(p(129));check(!PatternFavorites.contains(p(129)),"Overflow rejected");
        PatternFavorites.toggle(p(1));PatternFavorites.toggle(p(129));
        check(!PatternFavorites.contains(p(1))&&PatternFavorites.contains(p(129)),"Remove and replace at limit");
        reload();check(PatternFavorites.customPatterns().size()==128&&PatternFavorites.contains(p(129)),"Custom bits survive client restart");
        check(PatternFavorites.contains(10)&&PatternFavorites.contains(11)&&Files.size(file)<65536,"Presets preserved within bounded preference file");
        Files.writeString(file,"x="+"a".repeat(65537));reload();check(PatternFavorites.customPatterns().isEmpty(),"Oversized preference file rejected");
        System.out.println("PASS: 128 custom favorites, persistence, preset aliases, deduplication, removal/replacement and size limits; isolated scratch preferences.");
    }
}
