import org.spongepowered.asm.service.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.*;
import org.spongepowered.asm.launch.*;
import org.spongepowered.asm.launch.platform.container.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Isolated actual Mixin transformation against Forge's class, without a game window or JAR. */
public final class Mixin22Smoke {
    public static final class Properties implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey {}
        private final Map<IPropertyKey,Object> data=new HashMap<>();
        public IPropertyKey resolveKey(String name){return new Key(name);}
        @SuppressWarnings("unchecked") public <T>T getProperty(IPropertyKey key){return (T)data.get(key);}
        public void setProperty(IPropertyKey key,Object value){data.put(key,value);}
        public <T>T getProperty(IPropertyKey key,T fallback){T value=getProperty(key);return value==null?fallback:value;}
        public String getPropertyString(IPropertyKey key,String fallback){Object value=data.get(key);return value==null?fallback:value.toString();}
    }
    public static final class Service extends MixinServiceAbstract implements IClassProvider,IClassBytecodeProvider,ITransformerProvider,IClassTracker {
        private static final ClassLoader LOADER=Mixin22Smoke.class.getClassLoader();
        public String getName(){return "TrafficEngine isolated Forge verification";}
        public boolean isValid(){return true;}
        public MixinEnvironment.Phase getInitialPhase(){return MixinEnvironment.Phase.DEFAULT;}
        public IClassProvider getClassProvider(){return this;}
        public IClassBytecodeProvider getBytecodeProvider(){return this;}
        public ITransformerProvider getTransformerProvider(){return this;}
        public IClassTracker getClassTracker(){return this;}
        public IMixinAuditTrail getAuditTrail(){return null;}
        public Collection<String> getPlatformAgents(){return List.of();}
        public IContainerHandle getPrimaryContainer(){return new ContainerHandleURI(new File(".").toURI());}
        public InputStream getResourceAsStream(String name){return LOADER.getResourceAsStream(name);}
        public URL[] getClassPath(){return new URL[0];}
        public Class<?> findClass(String name)throws ClassNotFoundException{return findClass(name,false);}
        public Class<?> findClass(String name,boolean init)throws ClassNotFoundException{return Class.forName(name,init,LOADER);}
        public Class<?> findAgentClass(String name,boolean init)throws ClassNotFoundException{return findClass(name,init);}
        public ClassNode getClassNode(String name)throws ClassNotFoundException,IOException{return getClassNode(name,false);}
        public ClassNode getClassNode(String name,boolean transform)throws ClassNotFoundException,IOException{
            try(var in=getResourceAsStream(name.replace('.','/')+".class")){
                if(in==null)throw new ClassNotFoundException(name);
                ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);return node;
            }
        }
        public Collection<ITransformer> getTransformers(){return List.of();}
        public Collection<ITransformer> getDelegatedTransformers(){return List.of();}
        public void addTransformerExclusion(String name){}
        public void registerInvalidClass(String name){}
        public boolean isClassLoaded(String name){return false;}
        public String getClassRestrictions(String name){return "";}
        public IMixinTransformer transformer(){return getInternal(IMixinTransformerFactory.class).createTransformer();}
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("mixin.env.disableRefMap","true");
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Service service=(Service)MixinService.getService();
        IMixinTransformer transformer=service.transformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        Mixins.addConfiguration("trafficengine-smoke22.mixins.json");
        String name="net.minecraftforge.client.model.lighting.QuadLighter";
        ClassNode target=service.getClassNode(name);transformer.transformClass(MixinEnvironment.getCurrentEnvironment(),name,target);
        if(target.methods.stream().noneMatch(m->m.name.contains("placementShade")))throw new AssertionError("Placement shader was not injected");
        if(target.methods.stream().anyMatch(m->m.name.contains("straightLightingNormal")))throw new AssertionError("Old array-local injector remains");
        System.out.println("PASS: actual Forge QuadLighter transformed by Mixin + MixinExtras; shade injection applied without primitive-array local analysis or AnalyzerException. No game/window/JAR.");
    }
}
