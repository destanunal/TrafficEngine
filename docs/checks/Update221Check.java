import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.client.StreetLampTargeting;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
import java.lang.reflect.*;

public class Update221Check {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static class World implements BlockGetter {
  Map<BlockPos,BlockState> blocks=new HashMap<>();int reads;
  public BlockState getBlockState(BlockPos p){reads++;return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
  public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("No entity reads");}
  public FluidState getFluidState(BlockPos p){return Fluids.EMPTY.defaultFluidState();}
  public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var registry=BuiltInRegistries.BLOCK;var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
  var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);Object previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
  Block[] b;
  try {frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
   b=new Block[]{new TrafficLightRequestButtonBlock(),new TrafficPoleBaseBlock(),new AsphaltSlope(RoadType.ASPHALT),new PaintedAsphaltSlope(RoadType.ASPHALT,null),new PedestrianRailingBlock(),new TrafficSignPostBlock(),new SlopedPedestrianRailingBlock(),new LedDeviceBlock(LedDeviceType.TRAFFIC_DISPLAY),new LedDeviceBlock(LedDeviceType.LARGE_TRAFFIC_DISPLAY),new LedDevicePartBlock()};
  }finally{holders.set(registry,previous);frozen.setBoolean(registry,was);}
  var sink=TrafficLightRequestButtonBlock.class.getDeclaredMethod("supportSink",BlockState.class,BlockGetter.class,BlockPos.class);sink.setAccessible(true);
  World world=new World();BlockPos pos=BlockPos.ZERO;
  // Check the actual Minecraft texture resolver, not just JSON string equality.
  for(int height=1;height<=8;height++) {
   String path="assets/trafficengine/models/block/request_button_sunk/traffic_light_request_button_"+height+".json";
   try(var stream=Update221Check.class.getClassLoader().getResourceAsStream(path)) {
    var model=net.minecraft.client.renderer.block.model.BlockModel.fromStream(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
    for(var element:model.getElements())for(var face:element.faces.values())
     check(!model.getMaterial(face.texture).texture().getPath().equals("missingno"),"All lowered faces resolve through Minecraft's named texture slots");
   }
  }
  for(int height=0;height<=8;height++)for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean lower:new boolean[]{false,true}) {
   var state=b[0].defaultBlockState().setValue(TrafficLightRequestButtonBlock.SINK,height).setValue(TrafficLightRequestButtonBlock.FACING,facing).setValue(TrafficLightRequestButtonBlock.LOWER_POLE,lower);
   VoxelShape housing=switch(facing) {
    case NORTH->Block.box(5.5,1,3.5,10.5,15,9.22);case SOUTH->Block.box(5.5,1,6.78,10.5,15,12.5);
    case EAST->Block.box(6.78,1,5.5,12.5,15,10.5);default->Block.box(3.5,1,5.5,9.22,15,10.5);
   };
   VoxelShape expected=Shapes.or(housing.move(0,-height/8D,0),TrafficLightRequestButtonBlock.SHAPE_COMMON);
   if(!lower)expected=Shapes.or(expected,TrafficLightRequestButtonBlock.SHAPE_COMMON.move(0,-height/8D,0));
   int reads=world.reads;
   var shape=state.getShape(world,pos);
   check(!Shapes.joinIsNotEmpty(shape,expected,BooleanOp.NOT_SAME),"Picking matches the assembled housing and owned pole, without a duplicate stem");
   check(shape==state.getShape(world,pos)&&world.reads==reads,"All 72 button shapes are cached without terrain reads");
  }
  var properHit=TrafficLightRequestButtonBlock.class.getDeclaredMethod("isProperHit",BlockState.class,Direction.class,double.class);
  properHit.setAccessible(true);
  for(int height=1;height<=8;height++)for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean lower:new boolean[]{false,true}) {
   var button=b[0].defaultBlockState().setValue(TrafficLightRequestButtonBlock.FACING,facing)
    .setValue(TrafficLightRequestButtonBlock.SINK,height).setValue(TrafficLightRequestButtonBlock.LOWER_POLE,lower);
   world.blocks.put(pos,button);
   double touchY=4.3/16-height/8D;
   Vec3 from=new Vec3(.5+2*facing.getStepX(),touchY,.5+2*facing.getStepZ());
   Vec3 to=new Vec3(.5-2*facing.getStepX(),touchY,.5-2*facing.getStepZ());
   BlockHitResult miss=BlockHitResult.miss(to,facing,BlockPos.containing(to));
   var picked=(BlockHitResult)StreetLampTargeting.pick(world,from,to,CollisionContext.empty(),miss,List.of(pos));
   check(picked!=miss&&picked.getBlockPos().equals(pos)&&picked.getDirection()==facing,"Lowered touch plate targets its own block in every direction");
   BlockHitResult click=StreetLampTargeting.interactionHit(world,picked);
   check(Math.abs(click.getLocation().y-.5)<=1&&click.getBlockPos().equals(pos),"Cross-cell button click fits the server's hit coordinate bounds");
   check((boolean)properHit.invoke(b[0],button,click.getDirection(),click.getLocation().y+height/8D),"Normalized hit still presses the shifted front");
   Vec3 obstacle=from.lerp(to,.1);
   BlockHitResult wall=new BlockHitResult(obstacle,facing,BlockPos.containing(obstacle),false);
   check(StreetLampTargeting.pick(world,from,to,CollisionContext.empty(),wall,List.of(pos))==wall,"Sunk button does not select through a nearer block");
  }
  world.blocks.clear();
  for(int layer=1;layer<=8;layer++) {
   var ground=b[3].defaultBlockState().setValue(AsphaltSlope.LAYERS,layer);world.blocks.put(pos.below(2),ground);
   BlockState[] supports={b[5].defaultBlockState(),b[1].defaultBlockState().setValue(TrafficPoleBaseBlock.SINK,9-layer),ground,b[4].defaultBlockState().setValue(PedestrianRailingBlock.SINK,8-layer),b[6].defaultBlockState()};
   for(int i=0;i<supports.length;i++) {
    world.blocks.put(pos.below(),supports[i]);int reads=world.reads;
    var original=b[0].defaultBlockState().setValue(TrafficLightRequestButtonBlock.ACTIVATED,true).setValue(TrafficLightRequestButtonBlock.POWERED,true).setValue(TrafficLightRequestButtonBlock.WATERLOGGED,true);
    var supported=TrafficLightRequestButtonBlock.supportedState(original,world,pos);
    check(supported.getValue(TrafficLightRequestButtonBlock.LOWER_POLE)==(i<2),"Only a full-height lower carrier suppresses the buried stem");
    check(supported.getValue(TrafficLightRequestButtonBlock.ACTIVATED)&&supported.getValue(TrafficLightRequestButtonBlock.POWERED)&&supported.getValue(TrafficLightRequestButtonBlock.WATERLOGGED),"Legacy/copy support repair preserves active, powered and water state");
    check(world.reads-reads<=2,"Support invalidation reads at most two blocks, without scanning");
    check(TrafficLightRequestButtonBlock.supportedState(supported,world,pos)==supported,"Repeated support updates are idempotent");
   }
  }
  world.blocks.clear();

  for(int layers=1;layers<=8;layers++) {
   var normal=b[2].defaultBlockState().setValue(AsphaltSlope.LAYERS,layers);var painted=b[3].defaultBlockState().setValue(AsphaltSlope.LAYERS,layers);
    check(com.destan.trafficengine.blueprint.BlueprintPlan.cost(normal)==layers&&com.destan.trafficengine.blueprint.BlueprintPlan.cost(painted)==layers,"Blueprint charges actual slope layers for plain and painted roads");
   check(TrafficPoleBaseBlock.sinkFor(normal)==TrafficPoleBaseBlock.sinkFor(painted),"Base height independent of road paint");
   for(BlockState support:new BlockState[]{normal,painted,b[1].defaultBlockState().setValue(TrafficPoleBaseBlock.SINK,9-layers)}) {
    int offset=(int)sink.invoke(null,support,world,pos);
    check(offset==(support.getBlock()==b[1]?9-layers:8-layers),"Button follows exact support offset");
    for(Direction facing:Direction.Plane.HORIZONTAL) {
     var state=b[0].defaultBlockState().setValue(TrafficLightRequestButtonBlock.FACING,facing).setValue(TrafficLightRequestButtonBlock.SINK,offset);
     var shape=state.getShape(world,pos);var plain=state.setValue(TrafficLightRequestButtonBlock.SINK,0).getShape(world,pos);
     check(!Shapes.joinIsNotEmpty(shape,Shapes.or(plain.move(0,-offset/8D,0),TrafficLightRequestButtonBlock.SHAPE_COMMON),BooleanOp.NOT_SAME),"Lowered housing retains a full-height upper pole connector");
     check(shape==state.getShape(world,pos),"Button reuses cached shape");
    }
   }
   world.blocks.put(pos.below(2),painted);
   check((int)sink.invoke(null,b[5].defaultBlockState(),world,pos)==8-layers,"Button follows a sign post over painted terrain");
  }
  var extension=TrafficSignPostBlock.class.getDeclaredMethod("needsBottomExtension",BlockState.class,BlockState.class);extension.setAccessible(true);
  for(int i:new int[]{4,6})check((boolean)extension.invoke(null,b[5].defaultBlockState(),b[i].defaultBlockState()),"Post joins railing from above");
  check(((PedestrianRailingBlock)b[4]).connectsTo(b[5].defaultBlockState(),false,Direction.NORTH),"Railing joins vertical pole");
  check(!((PedestrianRailingBlock)b[4]).connectsTo(b[5].defaultBlockState().setValue(TrafficSignPostBlock.AXIS,Direction.Axis.X),false,Direction.NORTH),"Horizontal pole is not a vertical fence anchor");
  var mount=TrafficSignPostBlock.class.getDeclaredMethod("canMountCeiling",BlockState.class,BlockGetter.class,BlockPos.class);mount.setAccessible(true);
  world.blocks.put(pos.above(),Blocks.STONE.defaultBlockState());
  check((boolean)mount.invoke(null,b[5].defaultBlockState(),world,pos),"Vertical post accepts a solid ceiling");
  check(!(boolean)mount.invoke(null,b[5].defaultBlockState().setValue(TrafficSignPostBlock.AXIS,Direction.Axis.X),world,pos),"Horizontal post does not grow a ceiling plate");
  world.blocks.remove(pos.above());
  check(!(boolean)mount.invoke(null,b[5].defaultBlockState(),world,pos),"No floating ceiling mount");
  for(boolean floor:new boolean[]{false,true}) {
   var plain=b[5].defaultBlockState().setValue(TrafficSignPostBlock.BASE_SUPPORT,floor);
   var mounted=plain.setValue(TrafficSignPostBlock.CEILING_SUPPORT,true);
   var full=plain.setValue(TrafficSignPostBlock.UP,true);
   var expected=Shapes.or(full.getShape(world,pos),Block.box(5.75,14.5,5.75,10.25,16,10.25),Block.box(6.5,13,6.5,9.5,14.5,9.5));
   check(!Shapes.joinIsNotEmpty(mounted.getShape(world,pos),expected,BooleanOp.NOT_SAME),"Ceiling plate matches inverted model; floor mount preserved");
   check(mounted.getShape(world,pos)==mounted.getShape(world,pos),"Ceiling outline reuses cached geometry");
  }
  var openPost=b[5].defaultBlockState();
  for(boolean extended:new boolean[]{false,true}) {
   var open=openPost.setValue(TrafficSignPostBlock.EXTEND_BOTTOM,extended);
   var connected=open.setValue(TrafficSignPostBlock.UP,true);
   var outline=open.getShape(world,pos);
   check(outline==open.getShape(world,pos)&&outline.max(Direction.Axis.Y)==(extended?10:12)/16D,"Short capped post reuses its matching shape");
   check(outline.min(Direction.Axis.Y)==(extended?-1:0),"Short post preserves its lower support");
   check(connected.getShape(world,pos).max(Direction.Axis.Y)==1,"Stacking equipment restores the full upper connection");
  }
  var instance=new com.destan.trafficengine.mixin.ParticleEngineMixin();
  var reduce=instance.getClass().getDeclaredMethod("trafficengine$reduceDestruction",BlockPos.class,BlockState.class,com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);reduce.setAccessible(true);
  var skip=instance.getClass().getDeclaredMethod("trafficengine$skipSomeBreakParticles",net.minecraft.client.particle.Particle.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);skip.setAccessible(true);
  int[] count={0};com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> emit=values->{for(int i=0;i<256;i++)try {var ci=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add",true);skip.invoke(instance,null,ci);if(!ci.isCancelled())count[0]++;}catch(Exception e){throw new RuntimeException(e);}return null;};
  reduce.invoke(instance,pos,b[0].defaultBlockState(),emit);check(count[0]==64,"Request button retains 25 percent of break particles");
  count[0]=0;reduce.invoke(instance,pos,b[7].defaultBlockState(),emit);check(count[0]==64,"Standard display retains 25 percent of break particles");
  count[0]=0;reduce.invoke(instance,pos,b[8].defaultBlockState(),emit);check(count[0]==32,"Large display retains 12.5 percent of break particles");
  count[0]=0;reduce.invoke(instance,pos,b[9].defaultBlockState(),emit);check(count[0]==32,"Display part retains 12.5 percent regardless of how it is broken");
  for(var state:b[6].getStateDefinition().getPossibleStates()){var shape=state.getShape(world,pos);check(shape==state.getShape(world,pos),"Flat atomic cache preserves shape identity");}
  System.out.println("PASS 2.2.1: "+checks+" terrain, cached shape, fence connection and 75% particle reduction assertions");
 }
}
