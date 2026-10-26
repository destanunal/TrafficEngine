import com.destan.trafficengine.client.model.EmissiveQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import java.lang.reflect.*;

/** Native handler regression; pair with the real Forge/Mixin startup probe. */
public final class EmissiveRenderRegressionCheck {
    static Object[] captured;
    static String path;
    static int checks;
    static final int FULL_BRIGHT = 0x00f000f0;
    static final VertexConsumer CONSUMER = (VertexConsumer) Proxy.newProxyInstance(
        VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            captured = args; path = "consumer"; return null;
        });
    static final Operation<Void> ORIGINAL = args -> { captured = args; path = "original"; return null; };
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static void call(String owner, String method, Object... args) throws Exception {
        Class<?> type=Class.forName("com.destan.trafficengine.mixin."+owner);
        Method handler=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
        handler.setAccessible(true);
        handler.invoke(Modifier.isStatic(handler.getModifiers())?null:type.getConstructor().newInstance(),args);
    }
    public static void main(String[] args) throws Exception {
        BakedQuad ordinary=new BakedQuad(new int[32],-1,Direction.NORTH,null,true);
        BakedQuad emissive=new EmissiveQuad(new BakedQuad(new int[32],-1,Direction.NORTH,null,true));
        for(String owner:new String[]{"EmissiveQuadRenderMixin","EmissiveItemRenderMixin"}) {
            call(owner,"trafficengine$emissiveQuad",CONSUMER,null,emissive,.7f,.8f,.9f,0,3,ORIGINAL);
            check(path.equals("consumer")); check(captured[2]==EmissiveQuad.BRIGHTNESS); check(captured[6]==EmissiveQuad.LIGHT);
            call(owner,"trafficengine$emissiveQuad",CONSUMER,null,ordinary,.7f,.8f,.9f,12,3,ORIGINAL);
            check(path.equals("original")); check(captured[2]==ordinary && captured[6].equals(12));
        }
        for(BakedQuad quad:new BakedQuad[]{ordinary,emissive}) {
            call("EmissiveItemRenderMixin","trafficengine$forgeEmissiveQuad",CONSUMER,null,quad,.7f,.8f,.9f,.4f,12,3,true,ORIGINAL);
            check(captured[7].equals(quad==emissive?FULL_BRIGHT:12)); check(captured[6].equals(.4f) && captured[9].equals(true));
        }
        float[] ao={.1f,.2f,.3f,.4f};int[] light={1,2,3,4};
        call("ColoredLightModelMixin","trafficengine$coloredSurface",CONSUMER,null,emissive,ao,.7f,.8f,.9f,light,3,true,ORIGINAL,null);
        check(captured[3]==EmissiveQuad.BRIGHTNESS && captured[7]==EmissiveQuad.LIGHT);
        System.out.println("Emissive rendering regression: "+checks+" checks passed");
    }
}
