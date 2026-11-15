import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.RoadSignLightPosition;
import com.destan.trafficengine.client.model.*;
import com.destan.trafficengine.item.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import org.joml.Vector3f;
import sun.misc.Unsafe;

/** Compare production paint with Minecraft's manual face bake and exercise real light channels. */
public final class Visual233Check {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Stroke extends RoadStrokeBakedModel {
  Stroke(TextureAtlasSprite sprite){super(null,sprite);}
 }
 static final class Casing extends RoadSignJoinedBakedModel {
  Casing(BakedModel model,net.minecraft.world.level.block.state.BlockState state){super(model,state);}
  List<BakedQuad> sample(int light){return lit(null,0,light);}
 }
 static void put(Object target,String name,Object value)throws Exception {
  Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
 }
 static TextureAtlasSprite sprite()throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  var contents=(SpriteContents)unsafe.allocateInstance(SpriteContents.class);
  put(contents,"width",16);put(contents,"height",16);
  var sprite=(TextureAtlasSprite)unsafe.allocateInstance(TextureAtlasSprite.class);
  put(sprite,"contents",contents);put(sprite,"u0",.125F);put(sprite,"u1",.375F);
  put(sprite,"v0",.25F);put(sprite,"v1",.5F);return sprite;
 }
 static float value(BakedQuad q,int vertex,int slot){return Float.intBitsToFloat(q.getVertices()[vertex*8+slot]);}
 static BakedQuad manual(TextureAtlasSprite sprite)throws Exception {
  var face=new BlockElementFace(null,1,"#overlay",new BlockFaceUV(new float[]{0,0,16,16},0));
  Method bake=Arrays.stream(FaceBakery.class.getMethods()).filter(m->m.getName().equals("bakeQuad")).findFirst().orElseThrow();
  Object[] args=new Object[bake.getParameterCount()];
  Object[] common={new Vector3f(0,0,0),new Vector3f(16,16.05F,16),face,sprite,Direction.UP,BlockModelRotation.X0_Y0,null,true};
  System.arraycopy(common,0,args,0,common.length);
  return (BakedQuad)bake.invoke(new FaceBakery(),args);
 }
 static float interpolate(BakedQuad manual,int slot,float x,float z) {
  // Minecraft's full-block top is affine; find the edge values rather than copying its UV formula.
  float at00=0,at10=0,at01=0;
  for(int i=0;i<4;i++) {
   float xx=value(manual,i,0),zz=value(manual,i,2),v=value(manual,i,slot);
   if(xx<.5&&zz<.5)at00=v;if(xx>.5&&zz<.5)at10=v;if(xx<.5&&zz>.5)at01=v;
  }
  return at00+x*(at10-at00)+z*(at01-at00);
 }
 public static void run(RoadInformationSignBlock[] signs)throws Exception {
  var sprite=sprite();var original=manual(sprite);var stroke=new Stroke(sprite);
  for(int style:new int[]{0,1,3})for(int length:new int[]{1,2,5}) {
   var geometry=RoadStrokeGeometry.between(new RoadPaintPlan.Cell(0,0,0),new RoadPaintPlan.Cell(0,0,length),1,style);
   check(!geometry.isEmpty(),"Straight and dashed fixtures have production geometry");
   for(float[] mesh:geometry.values())for(BakedQuad q:stroke.bake(mesh)) {
    check(q.getDirection()==Direction.UP&&q.isShade()==original.isShade(),"Native manual face normal and diffuse shading");
    Set<String> points=new HashSet<>();
    for(int v=0;v<4;v++) {
     float x=value(q,v,0),z=value(q,v,2);
     points.add(x+":"+z);
     for(int slot:new int[]{4,5})check(Math.abs(value(q,v,slot)-interpolate(original,slot,x,z))<1E-6,
      "Automatic paint uses the exact manual atlas texel coordinate, including dashed ends");
     check(q.getVertices()[v*8+6]==0,"Road paint retains natural world illumination");
    }
    check(points.size()==4,"Straight strokes are single native quads, not separately shaded triangle halves");
   }
  }
  float[] rectangle=RoadStrokeGeometry.single(new RoadPaintPlan.Cell(0,0,0),0,1,0,0);
  float[] old=new float[24];
  int[] triangleCorners={0,1,2,2,0,2,3,3};
  for(int v=0;v<8;v++)System.arraycopy(rectangle,triangleCorners[v]*3,old,v*3,3);
  var legacy=stroke.bake(old);check(legacy.size()==1,"Existing saved triangle pairs become one native rectangle without repainting");
  for(int v=0;v<4;v++)for(int slot:new int[]{4,5})check(Math.abs(value(legacy.get(0),v,slot)-interpolate(original,slot,value(legacy.get(0),v,0),value(legacy.get(0),v,2)))<1E-6,"Existing saved strokes share the original manual UVs");
  for(int mask=0;mask<16;mask++)for(int style=0;style<4;style++) {
   double[] ports=new double[4];Arrays.fill(ports,Double.NaN);
   for(int i=0;i<4;i++)if((mask&(1<<i))!=0)ports[i]=.5;
   float[] mesh=RoadAutoGeometry.connected(Direction.NORTH,style,0,ports);
   for(BakedQuad q:stroke.bake(mesh))for(int v=0;v<4;v++) {
    float u=(value(q,v,4)-sprite.getU0())/(sprite.getU1()-sprite.getU0());
    check(u>=.375-1E-5&&u<=.625+1E-5,"Bends, intersections and short dashes never sample transparent parts of the manual sprite");
   }
  }
  int[] carrier=new int[32];var plate=new BakedQuad(carrier,110,Direction.NORTH,sprite,false);
  BakedModel dummy=(BakedModel)Proxy.newProxyInstance(Visual233Check.class.getClassLoader(),new Class<?>[]{BakedModel.class},(p,m,a)->{
   if(m.getName().equals("getQuads"))return List.of(plate);
   throw new AssertionError("Unexpected model call "+m);
  });
  var casing=new Casing(dummy,signs[0].defaultBlockState());
  var bright=casing.sample(15<<20|7<<4);
  check(bright==casing.sample(15<<20|7<<4),"Repeated chunk illumination reuses the same bounded quad list");
  var dark=casing.sample(0);check(dark!=bright&&dark.get(0).getVertices()[6]==0,"Light changes rebake cached quads; old skylight is never retained");
  check(casing.sample(0)==dark,"Dark-world geometry is cached too");
  check(carrier[6]==0,"Lit variants never modify shared source quads");
  for(var sign:signs)for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean wall:new boolean[]{false,true})for(boolean diagonal:new boolean[]{false,true})for(boolean mounted:new boolean[]{false,true}) {
   var state=sign.defaultBlockState().setValue(RoadInformationSignBlock.FACING,facing)
    .setValue(RoadInformationSignBlock.WALL_MOUNTED,wall).setValue(RoadInformationSignBlock.DIAGONAL,diagonal)
    .setValue(RoadInformationSignBlock.POST_MOUNTED,mounted);
   BlockPos origin=new BlockPos(8,64,8),sample=RoadSignLightPosition.sample(origin,state);
   check(sample.getY()==origin.getY(),"Retains the original sign's light height");
   BlockAndTintGetter level=(BlockAndTintGetter)Proxy.newProxyInstance(Visual233Check.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},(proxy,m,args)->{
    return switch(m.getName()) {
     case "getBlockState"->net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
     case "getBrightness"->{check(sample.equals(args[1]),"Sample the exposed side, never the opaque rear block");yield args[0]==LightLayer.SKY?15:7;}
     default->throw new AssertionError("Unexpected world query "+m);
    };
   });
   if(!wall&&!mounted&&!diagonal)check(sample.equals(origin.relative(facing)),"Freestanding panels sample the exposed front neighbour");
   if(wall||mounted)check(sample.equals(origin),"Wall and post mounts sample their exposed cell, not the supporting rear block");
   int light=RoadSignJoinedBakedModel.surfaceLight(level,origin,state);
   check(light==(15<<20|7<<4),"Natural skylight and block light remain separate; no fullbright substitution");
   int[] data=new int[32];for(int i=0;i<32;i++)data[i]=i;
   var quad=new BakedQuad(data,110,facing,sprite,false);
   var lit=RoadSignJoinedBakedModel.surfaceLit(quad,light);
   check(lit!=quad&&Arrays.equals(quad.getVertices(),data),"Light correction never mutates shared native geometry");
   for(int v=0;v<4;v++)for(int s=0;s<8;s++)check(lit.getVertices()[v*8+s]==(s==6?light:data[v*8+s]),"Only illumination changes, not UVs, tint, normal or geometry");
   var metal=new BakedQuad(data,-1,facing,sprite,true);
   check(RoadSignJoinedBakedModel.surfaceLit(metal,light)==metal,"Mounts keep their normal native shading without extra quad copies");
  }
  System.out.println("PASS Visual233: "+checks+" native manual/automatic UV, rectangular stroke and exposed sign-light checks.");
 }
}
