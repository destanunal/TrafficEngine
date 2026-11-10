package com.destan.trafficengine.client.model;

import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Asset and packed-UV regression; no client/world startup or release packaging. */
public final class RailingPaintUVCheck {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}
    }
    static Method sodiumEncoder(Path jar)throws Exception {
        String original="net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/impl/CompactChunkVertex";
        ClassNode node=new ClassNode();
        try(var zip=new JarFile(jar.toFile());var in=zip.getInputStream(zip.getJarEntry(original+".class"))){new ClassReader(in).accept(node,0);}
        ClassWriter writer=new ClassWriter(0);String isolated="TrafficEngineExactSodiumEncoder";
        writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,isolated,null,"java/lang/Object",null);
        int found=0;
        for(var method:node.methods)if(method.name.equals("encodeTexture")||method.name.equals("sign")){
            method.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;
            for(var ins:method.instructions)if(ins instanceof MethodInsnNode call && call.owner.equals(original))call.owner=isolated;
            method.accept(writer);found++;
        }
        check(found==2,"Exact downloaded Sodium UV encoder and sign helper exist");writer.visitEnd();
        return new Loader().define(writer.toByteArray()).getMethod("encodeTexture",float.class,float.class);
    }
    static float decoded(Method encoder,float center,float value,int atlas)throws Exception {
        int packed=(int)encoder.invoke(null,center,value);
        // Exact 0.8.13 shader formula, default 8-bit GL subtexel precision.
        float shrink=(float)(1D/32768-1D/atlas/256);
        return (packed&32767)/32768F+((packed>>>15)!=0?1:-1)*shrink;
    }
    static JsonObject model(Path assets,String name)throws Exception {
        return JsonParser.parseString(Files.readString(assets.resolve("models/"+name+".json"))).getAsJsonObject();
    }
    public static void main(String[] args)throws Exception {
        Path assets=Path.of(args[0],"common/src/main/resources/assets/trafficengine");
        var image=ImageIO.read(assets.resolve("textures/block/railing_paint.png").toFile());
        check(image.getWidth()==32&&image.getHeight()==32,"Shared paint sprite is 32px, with a safe interior");
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)check(image.getRGB(x,y)==-1,"Paint base remains opaque neutral white");
        int aliases=0,faces=0;
        for(String folder:List.of("pedestrian_railing","pedestrian_railing_gate","sloped_pedestrian_railing")){
            try(var files=Files.list(assets.resolve("models/block/"+folder))){
                for(Path file:files.filter(p->p.getFileName().toString().startsWith("black")).toList()){
                    var child=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    check(child.getAsJsonObject("textures").get("metal").getAsString().equals("trafficengine:block/railing_paint"),"Every paint variant uses the guarded sprite");aliases++;
                    var geometry=child;
                    while(!geometry.has("elements"))geometry=model(assets,geometry.get("parent").getAsString().replace("trafficengine:",""));
                    for(var element:geometry.getAsJsonArray("elements"))for(var entry:element.getAsJsonObject().getAsJsonObject("faces").entrySet()){
                        var face=entry.getValue().getAsJsonObject();
                        if(face.get("texture").getAsString().equals("#metal")){
                            check(face.has("tintindex")&&face.get("tintindex").getAsInt()==0,"All paint surfaces retain the block dye index");faces++;
                        }
                    }
                }
            }
        }
        check(aliases==144&&faces>25000,"All standard, gate, joined and sloped variants covered without duplicate geometry");
        Random random=new Random(231);
        for(int stride:new int[]{8,9,12})for(int trial=0;trial<100;trial++){
            int[] data=new int[stride*4];for(int i=0;i<data.length;i++)data[i]=random.nextInt();int[] original=data.clone();
            RailingPaintUV.center(data,.25F,.75F);
            for(int i=0;i<data.length;i++)check(data[i]==(i%stride==4?Float.floatToRawIntBits(.25F):i%stride==5?Float.floatToRawIntBits(.75F):original[i]),"Only UV attributes change; normals, tint, geometry and light remain intact");
        }
        ClassNode hook=new ClassNode();
        try(var in=RailingPaintUVCheck.class.getClassLoader().getResourceAsStream("com/destan/trafficengine/mixin/EmissiveFaceBakeryMixin.class")){new ClassReader(in).accept(hook,0);}
        long calls=hook.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i instanceof MethodInsnNode call&&call.owner.equals("com/destan/trafficengine/client/model/RailingPaintUV")&&call.name.equals("prepare")).count();
        check(calls==1,"The existing one-time bake hook applies the production UV guard");
        Method encoder=sodiumEncoder(Path.of(args[1]));
        int oldEscapes=0;
        for(int atlas:new int[]{1024,2048,4096,8192,16384,32768})for(int origin:new int[]{0,16,256,512}){
            // A 0.35-model-pixel cap on the old 1px sprite can collapse onto an atlas boundary.
            float oldCenter=(origin+.35F/16/2)/atlas;
            for(float inset:new float[]{.125F,.875F}) {
                float edge=(origin+.35F/16*inset)/atlas;
                float sample=decoded(encoder,oldCenter,edge,atlas)*atlas;
                if(sample<origin||sample>=origin+1)oldEscapes++;
            }
            float fixed=(origin+16F)/atlas;
            float quantized=decoded(encoder,fixed,fixed,atlas)*atlas;
            for(int mip=0;mip<=4;mip++){
                float coordinate=(quantized-origin)/(1<<mip);
                float size=32F/(1<<mip);
                check(coordinate>=.5F-1E-4&&coordinate<=size-.5F+1E-4,"Real Sodium packed UV stays inside white texels at every supported mip and atlas size");
            }
        }
        check(oldEscapes>0,"Reproduces old thin-cap UV leakage using actual Sodium bytecode");
        System.out.println("PASS Railing paint: "+checks+" assertions, "+faces+" tinted faces, "+oldEscapes+" old edge escapes reproduced; exact Sodium 0.8.13 encoder, 1K..32K atlases and mips 0..4. This is not a full modpack visual/FPS test.");
    }
}
