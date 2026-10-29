import com.destan.trafficengine.client.lighting.*;
import com.destan.trafficengine.mixin.SodiumColoredLightMixin;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.*;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.*;
import java.util.Arrays;

/** Tests the RGB handler directly; does not assert that a live Sodium mixin applied. */
public final class Sodium211Check {
    static void check(boolean b,String reason) {if(!b)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        var field=ClientColoredLighting.class.getDeclaredField("COLORS");field.setAccessible(true);
        ColoredLightIndex index=(ColoredLightIndex)field.get(null);
        var darkness=ClientColoredLighting.class.getDeclaredField("skyDarken");darkness.setAccessible(true);
        int previous=darkness.getInt(null);darkness.setInt(null,15);
        Object source=new Object();var cells=new Long2IntOpenHashMap();
        cells.put(BlockPos.ZERO.above().asLong(),RgbLightMath.pack(15,0,0));index.replace(source,cells);index.publish();
        int[] colors=new int[4],lights=new int[4];
        MutableQuadView quad=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),new Class<?>[]{MutableQuadView.class},(proxy,m,a)->{
            return switch(m.getName()) {
                case "lightFace" -> Direction.UP;
                case "color" -> {int i=(int)a[0];if(a.length==1)yield colors[i];colors[i]=(int)a[1];yield proxy;}
                case "lightmap" -> {int i=(int)a[0];if(a.length==1)yield lights[i];lights[i]=(int)a[1];yield proxy;}
                default -> throw new UnsupportedOperationException(m.getName());
            };
        });
        SodiumColoredLightMixin handler=new SodiumColoredLightMixin(){};
        var pos=SodiumColoredLightMixin.class.getDeclaredField("pos");pos.setAccessible(true);pos.set(handler,BlockPos.ZERO);
        var apply=SodiumColoredLightMixin.class.getDeclaredMethod("trafficengine$rgb",Object.class,Object.class,boolean.class,Object.class,CallbackInfo.class);apply.setAccessible(true);
        try {
            Arrays.fill(colors,0xFFFFFFFF);Arrays.fill(lights,0x00F000F0);
            apply.invoke(handler,quad,null,false,null,new CallbackInfo("shadeQuad",false));
            for(int i=0;i<4;i++) {check(colors[i]==0xFFFF1F1F,"Full-bright terrain keeps RGB tint");check(lights[i]==0x00F000F0,"External brightness/skylight preserved");}
            Arrays.fill(colors,0xFFAABBCC);
            apply.invoke(handler,quad,null,true,null,new CallbackInfo("shadeQuad",false));
            for(int color:colors)check(color==0xFFAABBCC,"Self-emissive material untouched");
            index.remove(source);index.publish();
            apply.invoke(handler,quad,null,false,null,new CallbackInfo("shadeQuad",false));
            for(int color:colors)check(color==0xFFAABBCC,"Unlit terrain untouched");
            System.out.println("PASS Sodium RGB handler: 16 assertions; full-bright terrain, emissive exclusion and brightness preservation");
        } finally {index.remove(source);index.publish();darkness.setInt(null,previous);}
    }
}
