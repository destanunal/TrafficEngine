import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPaintToolButton;
import com.destan.trafficengine.client.screen.LedDeviceScreen;
import com.destan.trafficengine.client.screen.PixelPatternScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import sun.misc.Unsafe;

/** Real screen input handlers, without a Minecraft window, world or network connection. */
public final class DrawTool191Check {
    private static final Unsafe UNSAFE;
    static {
        try { Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); UNSAFE = (Unsafe)field.get(null); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception { field(owner, name).set(target, value); }
    private static void set(Object target, String name, Object value) throws Exception { set(target, target.getClass(), name, value); }
    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static <T extends Screen> T screen(Class<T> type) throws Exception {
        T result = type.cast(UNSAFE.allocateInstance(type));
        set(result, Screen.class, "children", new ArrayList<>());
        var brush = new com.destan.trafficengine.data.PixelBrush(8); set(result,"brush",brush);
        set(result,"sizeSlider",new com.destan.trafficengine.client.gui.components.TEIntegerSlider(0,0,120,1,8,1,1,n->net.minecraft.network.chat.Component.literal(n+" px"),brush::setSize));
        set(result, "pencilButton", new TEPaintToolButton(0, 0, 110, false, b -> {}));
        set(result, "eraserButton", new TEPaintToolButton(116, 0, 110, true, b -> {}));
        return result;
    }
    private static void tool(Screen screen, boolean erase) throws Exception {
        Method select = screen.getClass().getDeclaredMethod("selectTool", boolean.class); select.setAccessible(true); select.invoke(screen, erase);
        Method selected = TEButton.class.getDeclaredMethod("isSelected"); selected.setAccessible(true);
        check((boolean)selected.invoke(get(screen, "pencilButton")) == !erase, "Pencil selection follows the active tool");
        check((boolean)selected.invoke(get(screen, "eraserButton")) == erase, "Eraser selection follows the active tool");
    }
    private static long count(PixelPatternScreen screen) throws Exception {
        long total = 0; for (long row : (long[])get(screen, "bits")) total += Long.bitCount(row); return total;
    }
    private static void scroll(Screen screen,double x,double y,double delta)throws Exception {
        try{screen.getClass().getMethod("mouseScrolled",double.class,double.class,double.class).invoke(screen,x,y,delta);}
        catch(NoSuchMethodException modern){screen.getClass().getMethod("mouseScrolled",double.class,double.class,double.class,double.class).invoke(screen,x,y,0D,delta);}
    }
    private static void stencil() throws Exception {
        PixelPatternScreen screen = screen(PixelPatternScreen.class);
        set(screen, "bits", new long[4]); set(screen, "left", 10); set(screen, "top", 20); set(screen, "cell", 4); set(screen,"frameY",-30);
        tool(screen, false);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(72, 82, 0, 60, 60); screen.mouseReleased(72, 82, 0);
        check(count(screen) == 16, "Fast 16x16 pencil strokes have no gaps");
        screen.mouseClicked(12, 22, 1); screen.mouseDragged(72, 82, 1, 60, 60); screen.mouseReleased(72, 82, 1);
        check(count(screen) == 16, "Right click neither draws nor erases");
        tool(screen, true);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(72, 82, 0, 60, 60); screen.mouseReleased(72, 82, 0);
        check(count(screen) == 0, "The selected eraser clears a stroke using the primary button");
        tool(screen, false);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(100, 100, 0, 88, 78); screen.mouseDragged(72, 22, 0, -28, -78); screen.mouseReleased(72, 22, 0);
        check(count(screen) == 2, "Leaving and re-entering the canvas does not connect across the gap");
        screen.mouseClicked(0, 0, 0); screen.mouseDragged(12, 26, 0, 12, 26); screen.mouseReleased(12, 26, 0);
        check(count(screen) == 2, "Starting outside the canvas must not start a stroke");
        tool(screen, true); screen.mouseClicked(12, 22, 1); screen.mouseReleased(12, 22, 1);
        check(count(screen) == 2, "Right click does not apply the selected eraser either");
        set(screen,"bits",new long[4]); tool(screen,false);scroll(screen,36,46,1);scroll(screen,36,46,1);
        screen.mouseClicked(36,46,0);screen.mouseReleased(36,46,0);
        check(count(screen)==9,"Mouse wheel changes the real stencil footprint to 3 by 3");
        tool(screen,true);screen.mouseClicked(36,46,0);screen.mouseReleased(36,46,0);
        check(count(screen)==0,"Resized eraser clears exactly the same footprint");
    }
    private static LedDeviceBlock fixture() throws Exception {
        Field holders = field(MappedRegistry.class, "unregisteredIntrusiveHolders"), frozen = field(MappedRegistry.class, "frozen");
        boolean previousFrozen = frozen.getBoolean(BuiltInRegistries.BLOCK);
        Object previousHolders = holders.get(BuiltInRegistries.BLOCK);
        frozen.setBoolean(BuiltInRegistries.BLOCK, false); holders.set(BuiltInRegistries.BLOCK, new IdentityHashMap<>());
        try { return new LedDeviceBlock(LedDeviceType.LED_LIGHT); }
        finally { holders.set(BuiltInRegistries.BLOCK, previousHolders); frozen.setBoolean(BuiltInRegistries.BLOCK, previousFrozen); }
    }
    private static void led() throws Exception {
        LedDeviceScreen screen = screen(LedDeviceScreen.class);
        LedDeviceBlockEntity entity = (LedDeviceBlockEntity)UNSAFE.allocateInstance(LedDeviceBlockEntity.class);
        set(entity, BlockEntity.class, "blockState", fixture().defaultBlockState());
        set(screen, "blockEntity", entity); set(screen, "pixelColors", new int[64]); set(screen, "selectedColor", 0x1585D0);
        set(screen, "gridX", 10); set(screen, "gridY", 20); set(screen, "gridCell", 4); set(screen,"contentX",0);set(screen,"contentWidth",100);set(screen,"previewY",0);
        tool(screen, false);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(40, 50, 0, 28, 28); screen.mouseReleased(40, 50, 0);
        check(Long.bitCount((long)get(screen, "pixels")) == 8, "Fast LED pencil strokes have no gaps");
        int[] colors = (int[])get(screen, "pixelColors");
        for (int i = 0; i < 8; i++) check(colors[i*9] == 0xFF1585D0, "Pencil retains the chosen per-pixel colour");
        screen.mouseClicked(12, 22, 1); screen.mouseDragged(40, 50, 1, 28, 28); screen.mouseReleased(40, 50, 1);
        check(Long.bitCount((long)get(screen, "pixels")) == 8, "LED right click does not modify pixels");
        tool(screen, true);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(40, 50, 0, 28, 28); screen.mouseReleased(40, 50, 0);
        check((long)get(screen, "pixels") == 0, "LED eraser uses the selected tool with the primary button");
        tool(screen, false);
        screen.mouseClicked(12, 22, 0); screen.mouseDragged(100, 100, 0, 88, 78); screen.mouseDragged(40, 22, 0, -60, -78); screen.mouseReleased(40, 22, 0);
        check(Long.bitCount((long)get(screen, "pixels")) == 2, "LED re-entry leaves the intermediate cells intact");
        screen.mouseClicked(0, 0, 0); screen.mouseDragged(12, 26, 0, 12, 26); screen.mouseReleased(12, 26, 0);
        check(Long.bitCount((long)get(screen, "pixels")) == 2, "LED toolbar/outside press cannot start painting");
        set(screen,"pixels",0L);tool(screen,false);scroll(screen,26,38,1);scroll(screen,26,38,1);
        screen.mouseClicked(26,38,0);screen.mouseReleased(26,38,0);
        check(Long.bitCount((long)get(screen,"pixels"))==9,"Mouse wheel resizes the real LED pencil");
        tool(screen,true);screen.mouseClicked(26,38,0);screen.mouseReleased(26,38,0);
        check((long)get(screen,"pixels")==0,"Resized LED eraser footprint");
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        stencil(); led();
        System.out.println("PASS: real 16x16 stencil and 8x8 LED input; pencil/eraser selection, continuous strokes, right-click immunity, re-entry, colour and outside-press checks.");
    }
}
