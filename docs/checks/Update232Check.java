import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
import java.lang.reflect.*;

/** Production geometry/cache and neighbour-event checks, without packaging or a running world. */
public final class Update232Check {
 static int count;
 static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
 static Block[] fixtures()throws Exception {
  var registry=BuiltInRegistries.BLOCK;var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
  var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);Object previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
  try{frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
   return new Block[]{new WarningLightBlock(),new CurvedTrafficPoleBlock(),new TrafficSignPostBlock(),new TrafficLightBlock(),new TrafficSignBlock(),new RoadInformationSignBlock(RoadInformationSignBlock.Kind.DIRECTION),new LedDeviceBlock(LedDeviceType.LED_LIGHT),new ChevronPanelBlock(),new DoubleSidedTrafficSignBlock()};
  }finally{holders.set(registry,previous);frozen.setBoolean(registry,was);}
 }
 static LevelAccessor world(int[] calls){
  return (LevelAccessor)Proxy.newProxyInstance(Update232Check.class.getClassLoader(),new Class<?>[]{LevelAccessor.class},(proxy,m,args)->{
   if(m.getName().equals("isClientSide"))return false;
   if(m.getName().equals("getBlockState")&&calls.length>1){calls[1]++;return Blocks.AIR.defaultBlockState();}
   if(m.getName().equals("scheduleTick")){calls[0]++;return null;}
   throw new AssertionError("Unexpected terrain read/work: "+m.getName());
  });
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Block[] b=fixtures();
  var warning=(WarningLightBlock)b[0];var curved=(CurvedTrafficPoleBlock)b[1];
  for(var model:TrafficLightModel.values())for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean horizontal:new boolean[]{false,true})for(boolean diagonal:new boolean[]{false,true})for(boolean hanging:new boolean[]{false,true})for(boolean compact:new boolean[]{false,true}) {
   var s=warning.defaultBlockState().setValue(TrafficLightBlock.MODEL,model).setValue(TrafficLightBlock.FACING,facing).setValue(WarningLightBlock.HORIZONTAL,horizontal).setValue(TrafficLightBlock.DIAGONAL,diagonal).setValue(WarningLightBlock.HANGING,hanging).setValue(WarningLightBlock.COMPACT,compact);
   var shape=warning.getShape(s,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty());
   check(shape==warning.getShape(s,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty()),"Warning shapes are cached");
   check(shape==warning.getCollisionShape(s,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty()),"Picking and collision follow the same housing");
   check(warning.getTicker(null,s,null)==null,"Warning light has no idle ticker");
   float[] outline=EquipmentOutlineGeometry.edges(s);
   check(outline.length==216,"Exactly two clean frames: housing and exposed stem");
   check(outline==EquipmentOutlineGeometry.edges(s),"Outline buffer reused");
   var wet=s.setValue(TrafficLightBlock.WATERLOGGED,true);check(outline==EquipmentOutlineGeometry.edges(wet),"Water does not duplicate outline cache entries");
   check(PlacementParticles.straight(s)==s.setValue(TrafficLightBlock.DIAGONAL,false),"Diagonal particles use the identical straight warning state");
   double mount=WarningLightBlock.mountingOffset(s);double end=(hanging?16:0)+mount;
   double stemX=7.05/16,stemZ=7.05/16;
   // A corner of the square stem stays filled even though the housing is diagonal.
   var unrotated=s.setValue(TrafficLightBlock.FACING,Direction.NORTH);
   var northShape=warning.getShape(unrotated,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty());
   check(!Shapes.joinIsNotEmpty(northShape,Shapes.box(stemX,((hanging?15:.5)+mount)/16,stemZ,7.15/16,((hanging?15.1:.6)+mount)/16,7.15/16),BooleanOp.ONLY_SECOND),"Diagonal head retains the ordinary square carrier at the join");
   check(Math.abs((hanging?shape.bounds().maxY:shape.bounds().minY)-end/16)<1e-8,"Stem reaches its supporting cell boundary");
   if(!diagonal&&facing==Direction.NORTH) {
    check(Math.abs(shape.bounds().minZ-6.4/16)<1e-8,"Straight hitbox includes the front lens at Z=6.42");
    check(Math.abs(shape.bounds().maxZ-9.5/16)<1e-8,"Straight hitbox ends at the actual housing back");
    double x=model.getLightsCount()!=1&&horizontal?2.5:5.5;
    check(Math.abs(shape.bounds().minX-x/16)<1e-8,"Warning outline uses warning dimensions, not the traffic-light case");
   }
  }
  for(Direction facing:Direction.Plane.HORIZONTAL)for(int part=0;part<6;part++) {
   var s=curved.defaultBlockState().setValue(CurvedTrafficPoleBlock.FACING,facing).setValue(CurvedTrafficPoleBlock.PART,part);
   check(curved.canAttach(s,BlockPos.ZERO,Direction.DOWN)==(part==0||part==4||part==5),"First straight cell accepts lower equipment");
   check(CurvedTrafficPoleBlock.isMountingPost(s,Direction.DOWN)==(part==0||part==4||part==5),"Sign/device mounting agrees with pole connectivity");
   if(part==4||part==5) {
    var mounted=s.setValue(CurvedTrafficPoleBlock.LAMP_SUPPORT,true);
    var shape=curved.getShape(mounted,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty());
    check(shape==curved.getShape(mounted,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,CollisionContext.empty()),"Mounted curved shape cached");
    check(shape.bounds().minY==0,"Mount reaches the block below without a gap");
    for(int i=2;i<7;i++) {
     int[] calls={0};var attached=b[i].defaultBlockState();if(b[i] instanceof LedDeviceBlock)attached=attached.setValue(LedDeviceBlock.POST_ABOVE,true);var result=curved.updateShape(s,Direction.DOWN,attached,world(calls),BlockPos.ZERO,BlockPos.ZERO.below());
     check(result.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"Post, light, sign, directional sign and LED enable the socket");
     check(calls[0]==1,"Only the existing coalesced integrity tick is scheduled; no terrain reads");
    }
    int[] calls={0};check(!curved.updateShape(mounted,Direction.DOWN,Blocks.AIR.defaultBlockState(),world(calls),BlockPos.ZERO,BlockPos.ZERO.below()).getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"Removing equipment removes the socket");
   }
  }
  for(Direction facing:Direction.Plane.HORIZONTAL)for(int part=0;part<6;part++) {
   var state=curved.defaultBlockState().setValue(CurvedTrafficPoleBlock.FACING,facing).setValue(CurvedTrafficPoleBlock.PART,part);
   check(curved.canAttach(state,BlockPos.ZERO,Direction.UP)==(part==4||part==5),"Upper equipment and post connectivity matches the arm");
   check(CurvedTrafficPoleBlock.isMountingPost(state,Direction.UP)==(part==4||part==5),"Upper mounting support agrees with connectivity");
   if(part!=4&&part!=5)continue;
   for(boolean lower:new boolean[]{false,true}) {
    var base=state.setValue(CurvedTrafficPoleBlock.LAMP_SUPPORT,lower);
    for(int i=2;i<9;i++) {
     int[] calls={0};var result=curved.updateShape(base,Direction.UP,b[i].defaultBlockState(),world(calls),BlockPos.ZERO,BlockPos.ZERO.above());
     check(result.getValue(CurvedTrafficPoleBlock.TOP_SUPPORT),"Upper post, light, regular/double sign, road sign, LED and chevron get a connector");
     check(result.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT)==lower,"Upper update preserves the lower mount");
     check(calls[0]==1,"Upper socket uses the existing integrity event without terrain lookups");
     var shape=result.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
     check(shape.bounds().maxY==1&&shape==result.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO),"Upper connector reaches the cell above with cached geometry");
     if(lower)check(shape.bounds().minY==0,"Both upper and lower connectors can coexist");
     int[] removed={0};var cleared=curved.updateShape(result,Direction.UP,Blocks.AIR.defaultBlockState(),world(removed),BlockPos.ZERO,BlockPos.ZERO.above());
     check(!cleared.getValue(CurvedTrafficPoleBlock.TOP_SUPPORT)&&cleared.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT)==lower,"Upper removal clears only the upper socket");
    }
   }
  }
  // Changing the upper neighbour updates the hanging mount once, without world reads/ticks.
  var standalone=warning.defaultBlockState();int[] warningCalls={0,0};
  var suspended=warning.updateShape(standalone,Direction.UP,b[2].defaultBlockState().setValue(TrafficSignPostBlock.AXIS,Direction.Axis.X),world(warningCalls),BlockPos.ZERO,BlockPos.ZERO.above());
  check(suspended.getValue(WarningLightBlock.HANGING),"Horizontal traffic post activates the upper connector");
  check(warning.updateShape(suspended,Direction.NORTH,Blocks.AIR.defaultBlockState(),world(warningCalls),BlockPos.ZERO,BlockPos.ZERO.north())==suspended,"Unrelated neighbours do not change the mount");
  check(!warning.updateShape(suspended,Direction.UP,Blocks.AIR.defaultBlockState(),world(warningCalls),BlockPos.ZERO,BlockPos.ZERO.above()).getValue(WarningLightBlock.HANGING),"Removing the upper post restores the short bottom stem");
  check(warningCalls[0]==0&&warningCalls[1]==1,"Only removal of an upper mount reads the single lower neighbour; no recurring task or terrain scan");
  for(int part:new int[]{4,5})check(warning.updateShape(standalone,Direction.UP,curved.defaultBlockState().setValue(CurvedTrafficPoleBlock.PART,part),world(warningCalls),BlockPos.ZERO,BlockPos.ZERO.above()).getValue(WarningLightBlock.HANGING),"Both curved-arm sockets support hanging warning lights");
  var firstBend=warning.updateShape(standalone,Direction.UP,curved.defaultBlockState().setValue(CurvedTrafficPoleBlock.PART,4),world(warningCalls),BlockPos.ZERO,BlockPos.ZERO.above());
  check(firstBend.getValue(WarningLightBlock.HANGING)&&!firstBend.getValue(WarningLightBlock.COMPACT),"Slightly inclined arm retains its fitted connector without coplanar flat-stem overlap");
  var post=(TrafficSignPostBlock)b[2];var horizontal=post.defaultBlockState().setValue(TrafficSignPostBlock.AXIS,Direction.Axis.X);
  for(boolean hanging:new boolean[]{false,true})for(boolean diagonal:new boolean[]{false,true}) {
   var ws=WarningLightBlock.mountingState(standalone.setValue(TrafficLightBlock.DIAGONAL,diagonal),hanging?horizontal:Blocks.AIR.defaultBlockState(),hanging?Blocks.AIR.defaultBlockState():horizontal);
   check(ws.getValue(WarningLightBlock.COMPACT),"Warning heads sit beside the actual horizontal arm surface");
   check(!post.connectsTo(null,BlockPos.ZERO,horizontal,ws,false,hanging?Direction.UP:Direction.DOWN),"Horizontal arm does not grow an extra half-block stem through the head");
   check(post.connectsTo(null,BlockPos.ZERO,post.defaultBlockState(),ws,false,Direction.DOWN),"Ordinary vertical traffic-post connectivity is retained");
   BlockGetter getter=(BlockGetter)Proxy.newProxyInstance(Update232Check.class.getClassLoader(),new Class<?>[]{BlockGetter.class},(proxy,m,a)->{
    if(m.getName().equals("getBlockState"))return BlockPos.ZERO.equals(a[0])?ws:Blocks.AIR.defaultBlockState();
    if(m.getName().equals("getBlockEntity"))return null;
    if(m.getName().equals("getFluidState"))return Fluids.EMPTY.defaultFluidState();
    throw new AssertionError("Unexpected picking lookup: "+m.getName());
   });
   double center=(hanging?18.5:-2.5)/16;
   var from=new net.minecraft.world.phys.Vec3(.5,center,-2);var to=new net.minecraft.world.phys.Vec3(.5,center,2);
   var miss=net.minecraft.world.phys.BlockHitResult.miss(to,Direction.NORTH,BlockPos.containing(to));
   var hit=com.destan.trafficengine.client.StreetLampTargeting.pick(getter,from,to,CollisionContext.empty(),miss,List.of(BlockPos.ZERO));
   check(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos().equals(BlockPos.ZERO),"Compact head outside its owner cell stays selectable and breakable");
   var normalized=com.destan.trafficengine.client.StreetLampTargeting.interactionHit(getter,(net.minecraft.world.phys.BlockHitResult)hit);
   check(normalized.getLocation().y>=0&&normalized.getLocation().y<=1&&normalized.getBlockPos().equals(BlockPos.ZERO),"Extended wrench click remains valid for the server");
   var wall=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(.5,center,-1),Direction.NORTH,new BlockPos(0,0,-1),false);
   check(com.destan.trafficengine.client.StreetLampTargeting.pick(getter,from,to,CollisionContext.empty(),wall,List.of(BlockPos.ZERO))==wall,"Compact-head picking preserves a nearer wall");
   int[] calls={0};var arm=curved.defaultBlockState().setValue(CurvedTrafficPoleBlock.PART,5).setValue(CurvedTrafficPoleBlock.LAMP_SUPPORT,true);
   check(!curved.updateShape(arm,Direction.DOWN,ws,world(calls),BlockPos.ZERO,BlockPos.ZERO.below()).getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"Curved arm omits its redundant long socket for compact warning heads");
  }
  var chevron=(ChevronPanelBlock)b[7];
  for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean single:new boolean[]{false,true}) {
   var state=chevron.defaultBlockState().setValue(ChevronPanelBlock.FACING,facing).setValue(ChevronPanelBlock.DIAGONAL,true).setValue(ChevronPanelBlock.SINGLE,single);
   var shape=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
   check(shape==state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO),"Chevron shape is reused");
   check(EquipmentOutlineGeometry.edges(state).length==216,"Chevron keeps the simple panel/stem outline");
   check(PlacementParticles.straight(state)==state.setValue(ChevronPanelBlock.DIAGONAL,false),"Chevron particles remain identical to the straight placement");
   var north=state.setValue(ChevronPanelBlock.FACING,Direction.NORTH).getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
   check(!Shapes.joinIsNotEmpty(north,Shapes.box(7.01/16,.5/16,7.01/16,7.1/16,.6/16,7.1/16),BooleanOp.ONLY_SECOND),"Chevron's square lower carrier aligns with a normal post");
  }
  // Exercise the actual particle filter rather than reproducing its keep/drop formula.
  var filter=new com.destan.trafficengine.mixin.ParticleEngineMixin();
  var destroy=filter.getClass().getDeclaredMethod("trafficengine$reduceDestruction",BlockPos.class,BlockState.class,com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);destroy.setAccessible(true);
  var add=filter.getClass().getDeclaredMethod("trafficengine$skipSomeBreakParticles",net.minecraft.client.particle.Particle.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);add.setAccessible(true);
  for(boolean diagonal:new boolean[]{false,true}) {
   var effectState=standalone.setValue(TrafficLightBlock.DIAGONAL,diagonal);
   int[] kept={0};
   com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> operation=arguments->{
    check(arguments[1]==standalone,"Both placements feed the same straight effect state to the engine");
    for(int i=0;i<100;i++)try {
     var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add",true);
     add.invoke(filter,null,callback);if(!callback.isCancelled())kept[0]++;
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    return null;
   };
   destroy.invoke(filter,BlockPos.ZERO,effectState,operation);
   check(kept[0]==25,"Warning breaking filter retains 25% for straight and diagonal heads");
  }
  for(int fixture:new int[]{4,8})for(boolean diagonal:new boolean[]{false,true}) {
   var effect=b[fixture].defaultBlockState().setValue(TrafficSignBlock.DIAGONAL,diagonal);int[] kept={0};
   com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> operation=arguments->{
    check(!((BlockState)arguments[1]).getValue(TrafficSignBlock.DIAGONAL),"Sign particle source remains the straight placement");
    for(int i=0;i<200;i++)try {
     var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add",true);add.invoke(filter,null,callback);if(!callback.isCancelled())kept[0]++;
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}return null;
   };
   destroy.invoke(filter,BlockPos.ZERO,effect,operation);
   check(kept[0]==(fixture==4?130:65),"Regular/double signs retain exactly 65% of their previous budget in both placements");
  }
  var afterEffect=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add",true);add.invoke(filter,null,afterEffect);
  check(!afterEffect.isCancelled(),"Warning reduction does not leak into other particles");
  var hot=standalone.setValue(TrafficLightBlock.DIAGONAL,true).setValue(WarningLightBlock.HANGING,true).setValue(WarningLightBlock.COMPACT,true);
  var cachedShape=warning.getShape(hot,null,BlockPos.ZERO,null);var cachedEdges=EquipmentOutlineGeometry.edges(hot);
  for(int i=0;i<100_000;i++){warning.getShape(hot,null,BlockPos.ZERO,null);EquipmentOutlineGeometry.edges(hot);}
  var memory=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
  if(memory.isThreadAllocatedMemorySupported()) {
   memory.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId(),before=memory.getThreadAllocatedBytes(id);
   for(int i=0;i<250_000;i++) {
    if(warning.getShape(hot,null,BlockPos.ZERO,null)!=cachedShape||EquipmentOutlineGeometry.edges(hot)!=cachedEdges)throw new AssertionError("Hot geometry lookup stopped reusing its cache");
   }
   long bytes=memory.getThreadAllocatedBytes(id)-before;check(bytes<4096,"Hot shape/outline lookups allocate no per-query objects: "+bytes);
   System.out.println("Warning cache allocation sample: "+bytes+" bytes / 250,000 shape+outline queries. Not an FPS measurement.");
  }
  System.out.println("PASS 2.3.2 equipment: "+count+" assertions; warning geometry, simple outlines, cache reuse, particle identity and first-flat-cell mounting. No FPS measurement.");
 }
}
