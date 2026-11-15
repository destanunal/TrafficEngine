import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.EquipmentOutlineGeometry;
import com.destan.trafficengine.client.model.RoadSignJoinedBakedModel;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** Exercises production join transforms against every authored casing face, not a second geometry implementation. */
public final class Update233Check {
 static int checks,closedCorners,bridgedRails,internalFaces;
 static ChevronPanelBlock chevron;static TrafficSignPostBlock post;static TrafficSignBlock sign;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static RoadInformationSignBlock[] fixtures() throws Exception {
  var registry=BuiltInRegistries.BLOCK;
  var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
  var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
  Object previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
  try{frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
   chevron=new ChevronPanelBlock();post=new TrafficSignPostBlock();sign=new TrafficSignBlock();
   return new RoadInformationSignBlock[]{new RoadInformationSignBlock(RoadInformationSignBlock.Kind.DIRECTION),
     new RoadInformationSignBlock(RoadInformationSignBlock.Kind.HIGHWAY),new RoadInformationSignBlock(RoadInformationSignBlock.Kind.LARGE)};
  }finally{holders.set(registry,previous);frozen.setBoolean(registry,was);}
 }
 static double[] world(double x,double y,double z,Direction facing) {
  return switch(facing){case EAST->new double[]{16-z,y,x};case SOUTH->new double[]{16-x,y,16-z};
   case WEST->new double[]{z,y,16-x};default->new double[]{x,y,z};};
 }
 static double canonicalX(int[] data,int offset,Direction facing) {
  double x=Float.intBitsToFloat(data[offset])*16,z=Float.intBitsToFloat(data[offset+2])*16;
  return switch(facing){case EAST->z;case SOUTH->16-x;case WEST->16-z;default->x;};
 }
 static boolean close(double a,double b){return Math.abs(a-b)<.00015;}
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var blocks=fixtures();Path assets=Path.of(args[0],"common/src/main/resources/assets/trafficengine");
  String[] names={"road_direction_sign","highway_direction_sign","large_road_direction_sign"};
  String[] variants={"","_connected","_mounted","_hanging","_wall"};
  for(int size=0;size<3;size++)for(String variant:variants)for(Direction facing:Direction.Plane.HORIZONTAL) {
   var state=blocks[size].defaultBlockState().setValue(RoadInformationSignBlock.FACING,facing)
    .setValue(RoadInformationSignBlock.POST_MOUNTED,variant.equals("_mounted"))
    .setValue(RoadInformationSignBlock.UP,variant.equals("_connected"))
    .setValue(RoadInformationSignBlock.WALL_MOUNTED,variant.equals("_wall"))
    .setValue(RoadInformationSignBlock.HANGING,variant.equals("_hanging"));
   double left=size==2?-24:-8,right=size==2?40:24;
   JsonObject model=JsonParser.parseString(Files.readString(assets.resolve("models/block/"+names[size]+variant+".json"))).getAsJsonObject();
   for(JsonElement element:model.getAsJsonArray("elements")) {
    JsonObject e=element.getAsJsonObject();String group=e.get("name").getAsString();
    for(var entry:e.getAsJsonObject("faces").entrySet()) {
     JsonObject face=entry.getValue().getAsJsonObject();
     JsonArray points;
     if(face.has("trafficengine_data"))points=face.getAsJsonObject("trafficengine_data").getAsJsonArray("vertices");
     else {
      // The middle face uses an ordinary vanilla cuboid; the corner strips use custom quads.
      points=new JsonArray();JsonArray from=e.getAsJsonArray("from"),to=e.getAsJsonArray("to");
      for(int[] xyz:new int[][]{{0,0,0},{1,0,0},{1,1,0},{0,1,0}}) {
       points.add((xyz[0]==0?from:to).get(0));points.add((xyz[1]==0?from:to).get(1));
       points.add((entry.getKey().equals("south")?to:from).get(2));
      }
     }
     int[] data=new int[32];
     for(int v=0;v<4;v++) {
      double[] p=world(points.get(v*3).getAsDouble(),points.get(v*3+1).getAsDouble(),points.get(v*3+2).getAsDouble(),facing);
      for(int axis=0;axis<3;axis++)data[v*8+axis]=Float.floatToRawIntBits((float)(p[axis]/16));
      for(int other=3;other<8;other++)data[v*8+other]=0x12340000+v*8+other;
      data[v*8+4]=Float.floatToRawIntBits(points.get(v*3).getAsFloat()/16);
      data[v*8+5]=Float.floatToRawIntBits(points.get(v*3+1).getAsFloat()/16);
     }
     check(RoadSignJoinedBakedModel.joinVertices(data,state,0)==data,"Unjoined quads are reused without allocation");
     check(RoadSignJoinedBakedModel.joinVertices(data,state.setValue(RoadInformationSignBlock.DIAGONAL,true),3)==data,"Diagonal casings are unchanged");
     for(int mask=1;mask<4;mask++) {
      int[] result=RoadSignJoinedBakedModel.joinVertices(data,state,mask);
      List<int[]> pieces=RoadSignJoinedBakedModel.connectedVertices(data,state,mask);
      check(pieces.size()<=3,"Bounded geometry: at most two tiny extra rail strips");
      if(result==null)check(pieces.isEmpty(),"Internal end faces are actually removed from the rendered mesh");
      if(pieces.size()>1) {
       check(pieces.get(0)==data,"Extending a rail retains its original quad and texture scale");
       double previousMin=Double.POSITIVE_INFINITY,previousMax=Double.NEGATIVE_INFINITY;
       for(int v=0;v<4;v++){double x=canonicalX(data,v*8,facing);previousMin=Math.min(previousMin,x);previousMax=Math.max(previousMax,x);}
       double fullMin=previousMin,fullMax=previousMax;
       for(int[] piece:pieces) {
        double a=Double.POSITIVE_INFINITY,b=Double.NEGATIVE_INFINITY;
        for(int v=0;v<4;v++) {
         double x=canonicalX(piece,v*8,facing);a=Math.min(a,x);b=Math.max(b,x);
         for(int k=0;k<v;k++) {
          double other=canonicalX(piece,k*8,facing);
          if(Math.abs(x-other)<1E-4)continue;
          double uv=Float.intBitsToFloat(piece[v*8+4])-Float.intBitsToFloat(piece[k*8+4]);
          check(Math.abs(uv/(x-other)-1./16)<1E-5,"Rail extensions preserve one texture pixel per model unit");
         }
        }
        if(piece!=data)check(b<=previousMin+1E-4||a>=previousMax-1E-4,"Rail strips meet at an edge without coplanar overlap");
        fullMin=Math.min(fullMin,a);fullMax=Math.max(fullMax,b);
       }
       if((mask&2)!=0&&close(previousMin,left+3))check(close(fullMin,left),"Rendered left rail closes the full gap");
       if((mask&1)!=0&&close(previousMax,right-3))check(close(fullMax,right),"Rendered right rail closes the full gap");
      }
      if(result==null){internalFaces++;continue;}
      for(int v=0;v<4;v++) {
       double x=canonicalX(result,v*8,facing);
       check(x>=left-.00015&&x<=right+.00015,"Joined geometry stays within the physical sign width");
       check(result[v*8+1]==data[v*8+1],"Joining does not move or slope the plate");
       for(int other=3;other<8;other++)check(result[v*8+other]==data[v*8+other],"Tint, UV, light and shade attributes are retained");
       if(group.equals("rear_panel")) {
        if((mask&2)!=0)check(x>left+.6671||close(x,left),"Every touching rear corner reaches the left seam");
        if((mask&1)!=0)check(x<right-.6671||close(x,right),"Every touching rear corner reaches the right seam");
        if(close(x,left)||close(x,right))closedCorners++;
       }
       if(group.endsWith("_rail")) {
        if((mask&2)!=0)check(!close(x,left+3),"Left rear rail has no three-unit gap");
        if((mask&1)!=0)check(!close(x,right-3),"Right rear rail has no three-unit gap");
        if(close(x,left)||close(x,right))bridgedRails++;
       }
      }
     }
    }
   }
   if(variant.equals("_wall")) {
    var shape=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
    check(shape==state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO),"Wall picking/collision volumes are cached");
    var bounds=shape.bounds();double near=switch(facing){case EAST->bounds.minX;case SOUTH->bounds.minZ;case WEST->1-bounds.maxX;default->1-bounds.maxZ;};
    check(Math.abs(near)<1E-8,"Wall casing touches its supporting block with no rear gap");
    double thickness=facing.getAxis()==Direction.Axis.X?bounds.getXsize():bounds.getZsize();
    check(Math.abs(thickness-.5/16)<1E-8,"Wall hitbox contains only the thin panel, with no hidden pole");
    float[] outline=EquipmentOutlineGeometry.edges(state);
    check(outline==EquipmentOutlineGeometry.edges(state),"Wall selection frame reused");
    check(!blocks[size].canConnect(state,Direction.UP),"Wall signs cannot grow a carrier through the wall");
   }
  }
  for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean diagonal:new boolean[]{false,true})for(boolean single:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true}) {
   var board=chevron.defaultBlockState().setValue(ChevronPanelBlock.FACING,facing).setValue(ChevronPanelBlock.DIAGONAL,diagonal).setValue(ChevronPanelBlock.SINGLE,single).setValue(ChevronPanelBlock.UPPER_CONNECTION,upper);
   var shape=board.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
   check(shape==board.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO),"Chevron collision/picking remains cached");
   check(EquipmentOutlineGeometry.edges(board).length==(upper?324:216),"Panel and exposed carrier frames only, no hidden carrier seams");
   check(chevron.updateShape(board,Direction.UP,post.defaultBlockState(),null,BlockPos.ZERO,BlockPos.ZERO.above()).getValue(ChevronPanelBlock.UPPER_CONNECTION),"Adding an upper post extends the support through neighbour updates only");
   check(!chevron.updateShape(board,Direction.UP,Blocks.AIR.defaultBlockState(),null,BlockPos.ZERO,BlockPos.ZERO.above()).getValue(ChevronPanelBlock.UPPER_CONNECTION),"Removing an upper post retracts the support");
   if(!upper)check(!Shapes.joinIsNotEmpty(shape,Shapes.box(7.01/16,.94,7.01/16,8.99/16,.99,8.99/16),BooleanOp.AND),"No invisible stem hitbox above a standalone panel");
   double panelBottom=single?2:4,stemTop=16-panelBottom-1.4;
   var localMount=Shapes.box(-2,-2,-2,2,stemTop/16,2);
   var shortShape=chevron.getShape(board.setValue(ChevronPanelBlock.UPPER_CONNECTION,false),EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty());
   var tallShape=chevron.getShape(board.setValue(ChevronPanelBlock.UPPER_CONNECTION,true),EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty());
   check(!Shapes.joinIsNotEmpty(Shapes.join(shortShape,localMount,BooleanOp.AND),Shapes.join(tallShape,localMount,BooleanOp.AND),BooleanOp.NOT_SAME),"Adding an upper pole never changes the lower stem, collar or mount thickness");
   double[] collarPoint=world(6.8,panelBottom+1.15,8,facing);
   var collarSample=Shapes.box((collarPoint[0]-.01)/16,(collarPoint[1]-.01)/16,(collarPoint[2]-.01)/16,(collarPoint[0]+.01)/16,(collarPoint[1]+.01)/16,(collarPoint[2]+.01)/16);
   check(!Shapes.joinIsNotEmpty(shape,collarSample,BooleanOp.ONLY_SECOND),"The fixed square clamp is pickable in every orientation");
   for(Direction side:new Direction[]{Direction.UP,Direction.DOWN}) {
    check(chevron.canConnect(board,side)&&chevron.canAttach(board,BlockPos.ZERO,side),"Accepts posts and signs above and below");
    check(post.connectsTo(null,BlockPos.ZERO,post.defaultBlockState(),board,false,side),"Normal post connects to the chevron carrier");
    check(sign.canConnect(sign.defaultBlockState(),side),"Normal traffic sign retains the matching carrier connection");
   }
   check(!Shapes.joinIsNotEmpty(shape,Shapes.box(7.01/16,0,7.01/16,8.99/16,upper?1:stemTop/16,8.99/16),BooleanOp.ONLY_SECOND),"The short mast covers the entire full-thickness bracket; extend only for upper devices");
  }
  Visual233Check.run(blocks);
  check(closedCorners>1000&&bridgedRails>500&&internalFaces>100,"Actual corner, rail and internal-end fixtures were exercised");
  System.out.println("PASS Update233: "+checks+" production geometry/attribute/cache checks; "+closedCorners+" closed corners, "+bridgedRails+" rail ends, "+internalFaces+" hidden internal faces.");
 }
}
