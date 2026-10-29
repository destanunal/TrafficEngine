import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.destan.trafficengine.client.model.PlacementShadeQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import java.lang.reflect.*;
import java.util.*;

/** Real loader formula/target checks, including the old visible 45-degree shade discontinuity. */
public final class Platform22LightingCheck {
    static int count;
    static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    static ClassNode read(String name)throws Exception {
        ClassNode node=new ClassNode();
        try(var in=Platform22LightingCheck.class.getClassLoader().getResourceAsStream(name+".class")){
            if(in==null)throw new AssertionError("Missing class "+name);new ClassReader(in).accept(node,0);
        }return node;
    }
    static boolean annotation(MethodNode m,String name){
        for(var annotations:Arrays.asList(m.visibleAnnotations,m.invisibleAnnotations))if(annotations!=null)
            for(var a:annotations)if(a.desc.endsWith('/'+name+';'))return true;
        return false;
    }
    static BakedQuad quad(int normal) {
        int[] packed=new int[32];for(int i=0;i<4;i++)packed[i*8+7]=normal;
        return new BakedQuad(packed,-1,Direction.getNearest((byte)normal,(byte)(normal>>8),(byte)(normal>>16)),null,true);
    }
    public static void main(String[] args)throws Exception {
        boolean neo=args[0].equals("neoforge");
        String name=neo?"net/neoforged/neoforge/client/model/lighting/QuadLighter":"net/minecraftforge/client/model/lighting/QuadLighter";
        ClassNode type=read(name),hook=read("com/destan/trafficengine/mixin/PlatformColoredLightMixin");
        MethodNode method=type.methods.stream().filter(m->neo?m.name.equals("computeLightingForQuad")&&m.desc.equals("([IZ)V"):m.name.equals("process")).findFirst().orElseThrow();
        int shadeCalls=0;
        for(var ins:method.instructions)if(ins instanceof MethodInsnNode call&&call.name.equals("getShade")&&call.desc.equals("(FFFZ)F")){
            check(call.owner.equals("net/minecraft/world/level/BlockAndTintGetter"),"Shade invocation owner matches expression hook");shadeCalls++;
        }
        check(shadeCalls==1,"One float-normal shade invocation");
        check(hook.methods.stream().noneMatch(m->annotation(m,"ModifyVariable")),"Do not trigger Forge primitive-array local-table analysis");
        check(hook.methods.stream().anyMatch(m->annotation(m,"ModifyExpressionValue")),"Correct the shade expression without reconstructing locals");
        if(!neo)check(hook.methods.stream().filter(m->m.name.equals("trafficengine$placementShade")).allMatch(m->m.desc.equals("(FLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;I)F")),"Forge captures original arguments directly");
        var shader=Class.forName(name.replace('/','.')).getMethod("calculateShade",float.class,float.class,float.class,boolean.class);
        for(boolean constant:new boolean[]{false,true}){
            BlockAndTintGetter level=(BlockAndTintGetter)Proxy.newProxyInstance(Platform22LightingCheck.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},(proxy,m,a)->{
                if(m.getName().equals("getShade")&&a[0] instanceof Direction dir){
                    if(!(boolean)a[1])return constant?.9F:1F;
                    return switch(dir){case UP->constant?.9F:1F;case DOWN->constant?.9F:.5F;case NORTH,SOUTH->.8F;default->.6F;};
                }throw new AssertionError("Unexpected world read: "+m);
            });
            for(Direction side:Direction.values()){
                int normal=(side.getStepX()*127&255)|((side.getStepY()*127&255)<<8)|((side.getStepZ()*127&255)<<16);
                float actual=PlacementShadeQuad.smooth(quad(normal)).shade(level);
                float expected=(float)shader.invoke(null,(float)side.getStepX(),(float)side.getStepY(),(float)side.getStepZ(),constant);
                check(Math.abs(actual-expected)<1e-6,"Straight cardinal endpoints match the real loader shader");
            }
            for(int axis:new int[]{0,2})for(int sign:new int[]{-1,1}){
                float previous=-1;
                for(int step=0;step<=360;step++){
                    double angle=Math.toRadians(step*.25);int horizontal=(int)Math.round(127*Math.cos(angle)),vertical=sign*(int)Math.round(127*Math.sin(angle));
                    int normal=((axis==0?horizontal:0)&255)|((vertical&255)<<8)|(((axis==2?horizontal:0)&255)<<16);
                    float x=(byte)normal,y=(byte)(normal>>8),z=(byte)(normal>>16),len=(float)Math.sqrt(x*x+y*y+z*z);
                    float actual=PlacementShadeQuad.smooth(quad(normal)).shade(level);
                    float expected=(float)shader.invoke(null,x/len,y/len,z/len,constant);
                    check(Math.abs(actual-expected)<1e-6,"Continuous curved shade matches real loader diffuse formula");
                    if(previous>=0)check(Math.abs(actual-previous)<.018F,"No 45-degree cardinal shade jump");
                    previous=actual;
                }
            }
        }
        System.out.println("PASS "+count+" checks: real "+args[0]+" shader, continuous bend shading, straight endpoints and expression-only hook without primitive-array local analysis. Live startup is a separate check.");
    }
}
