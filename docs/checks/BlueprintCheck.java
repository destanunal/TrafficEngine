import com.destan.trafficengine.blueprint.*;
import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.item.*;
import java.util.*;

/** Exercises production palette, rotation, material, assembly and internal-link code without packaging a mod. */
public final class BlueprintCheck {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static CompoundTag location(BlockPos p,String dim){var t=new CompoundTag();t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());t.putString("dimension",dim);t.putInt("SignalFace",1);return t;}
 static Block[] fixtures()throws Exception {
  var registry=BuiltInRegistries.BLOCK;var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);var previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
  try{frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());return new Block[]{new CurvedTrafficPoleBlock(),new LedDeviceBlock(LedDeviceType.TRAFFIC_DISPLAY),new LedDeviceBlock(LedDeviceType.LARGE_TRAFFIC_DISPLAY),new LedDevicePartBlock(),new TrafficSignPostBlock(),new TrafficLightBlock()};}
  finally{holders.set(registry,previous);frozen.setBoolean(registry,was);}
 }
 static BlueprintPlan plan(List<BlueprintPlan.Cell> cells){return new BlueprintPlan("Cadde • Şehir",new BlockPos(16,16,16),new BlockPos(-30,64,-80),"minecraft:overworld",cells);}

 static void simplifiedToolAndUndo()throws Exception {
  var first=plan(List.of(new BlueprintPlan.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null))).save();
  var active=first.copy();active.putString("Name","Active plan");
  CompoundTag data=new CompoundTag();data.put("Plan0",first);data.put("Plan3",active);data.putInt("Slot",3);
  data.putLong("First",1);data.putLong("Second",2);data.putString("SelectionDimension","minecraft:overworld");data.putBoolean("Placing",true);data.putInt("Spacing",8);data.putInt("Copies",4);
  BlueprintToolItem.singlePlan(data);
  check(BlueprintPlan.SLOTS==1&&data.getInt("Slot")==0&&data.getCompound("Plan0").getString("Name").equals("Active plan"),"Upgrade keeps the old active plan in the single slot");
  for(int i=1;i<4;i++)check(!data.contains("Plan"+i),"Extra plan slots removed");
  BlueprintToolItem.clearSelection(data);
  check(!data.contains("First")&&!data.contains("Second")&&!data.contains("SelectionDimension")&&!data.getBoolean("Placing"),"Reset clears both corners, dimension and placement mode");
  check(!data.contains("Plan0")&&data.getInt("Spacing")==8&&data.getInt("Copies")==4,"Reset clears the automatic plan but preserves repeat settings");
  BlueprintToolItem.singlePlan(data);check(!data.contains("Plan0"),"Migration does not resurrect a reset selection");
  data.put("Plan0",active);var a=new BlockPos(2,64,3);var b=new BlockPos(4,66,7);
  check(!BlueprintToolItem.selectCorner(data,a,"minecraft:overworld")&&!data.contains("Plan0")&&!data.contains("Second"),"First corner invalidates the old automatic plan");
  check(BlueprintToolItem.selectCorner(data,b,"minecraft:overworld")&&BlockPos.of(data.getLong("First")).equals(a)&&BlockPos.of(data.getLong("Second")).equals(b),"Second matching corner immediately requests server capture");
  check(!BlueprintToolItem.selectCorner(data,a,"minecraft:the_nether")&&!data.contains("Second")&&data.getString("SelectionDimension").equals("minecraft:the_nether"),"Changing dimension starts a new selection instead of copying the old one");
  check(BlueprintToolItem.spacing(new CompoundTag())==8,"Default spacing remains eight blocks");
  for(int value:new int[]{Integer.MIN_VALUE,-1,0,1,16,31,32,33,64,Integer.MAX_VALUE}){
   var settings=new CompoundTag();settings.putInt("Copies",value);settings.putInt("Spacing",value);
   check(BlueprintToolItem.copies(settings)==Math.max(1,Math.min(32,value)),"Copy count clamps safely to 1..32");
   check(BlueprintToolItem.spacing(settings)==Math.max(0,Math.min(32,value)),"Spacing clamps safely to 0..32, including old 64-block settings");
  }
  for(int direction=0;direction<6;direction++)check(BlueprintToolItem.direction(direction)==(direction==4?Direction.UP:direction==5?Direction.DOWN:Direction.from2DDataValue(direction)),"Preserve old directions and add up/down");
  var settings=new CompoundTag();
  check(BlueprintToolItem.mode(settings)==0,"Legacy tools default to strict empty-area placement");
  settings.putInt("PlacementMode",1);check(BlueprintToolItem.anchor(settings,BlockPos.ZERO,Direction.UP).equals(BlockPos.ZERO.above()),"Skip placement uses the clicked face");
  settings.putInt("PlacementMode",2);check(BlueprintToolItem.anchor(settings,BlockPos.ZERO,Direction.UP).equals(BlockPos.ZERO),"Replace targets the clicked block, matching preview");
  check(BlueprintPlacement.skipped(1,Blocks.STONE.defaultBlockState(),Blocks.DIRT.defaultBlockState()),"Skip leaves occupied cells unchanged");
  check(!BlueprintPlacement.skipped(1,Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState()),"Skip fills only empty cells");
  check(!BlueprintPlacement.skipped(2,Blocks.STONE.defaultBlockState(),Blocks.DIRT.defaultBlockState()),"Replace processes different existing blocks");
  check(BlueprintPlacement.skipped(2,Blocks.STONE.defaultBlockState(),Blocks.STONE.defaultBlockState()),"Identical ordinary blocks consume no materials");
  for(int mode=0;mode<3;mode++)for(int direction=0;direction<6;direction++){
   var sent=new BlueprintPacket(4,3,32,0,direction,mode).serializeNbt();
   var received=new BlueprintPacket(com.destan.trafficengine.network.PacketStatus.OK);received.deserializeNbt(sent);
   check(sent.equals(received.serializeNbt()),"Mode, zero gap and six directions survive the actual network codec");
  }
  CompoundTag saved=new CompoundTag();saved.putString("Text","Original");
  var cell=new BlueprintUndo.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),saved);
  check(BlueprintUndo.unchanged(cell,Blocks.STONE.defaultBlockState(),saved.copy()),"Undo accepts identical data snapshots");
  var edited=saved.copy();edited.putString("Text","Edited");
  check(!BlueprintUndo.unchanged(cell,Blocks.STONE.defaultBlockState(),edited),"Undo preserves edited block-entity data");
  check(!BlueprintUndo.unchanged(cell,Blocks.DIRT.defaultBlockState(),saved),"Undo preserves replaced blocks");
  check(!BlueprintUndo.unchanged(cell,Blocks.STONE.defaultBlockState(),null),"Undo preserves missing/replaced block entities");
  BlueprintUndo.clear();
  var field=BlueprintUndo.class.getDeclaredField("HISTORY");field.setAccessible(true);var cache=(Map<?,?>)field.get(null);
  var bytesField=BlueprintUndo.class.getDeclaredField("cachedBytes");bytesField.setAccessible(true);
  var originalCell=new BlueprintUndo.Cell(BlockPos.ZERO,Blocks.DIRT.defaultBlockState(),null);
  var replaced=new BlueprintUndo.Copy(List.of(cell),List.of(),128,List.of(originalCell));
  check(replaced.before().get(0).state().is(Blocks.DIRT)&&replaced.cells().get(0).state().is(Blocks.STONE),"Undo stores old and placed states separately");
  var tiny=new BlueprintUndo.Copy(List.of(cell),List.of(),64);
  check(tiny.before().isEmpty(),"Legacy undo constructor retains air-only removal behavior");
  java.util.UUID oldest=java.util.UUID.randomUUID();BlueprintUndo.remember(oldest,"minecraft:overworld",List.of(tiny));
  for(int i=0;i<BlueprintUndo.MAX_PLAYERS;i++)BlueprintUndo.remember(java.util.UUID.randomUUID(),"minecraft:overworld",List.of(tiny));
  check(cache.size()==BlueprintUndo.MAX_PLAYERS&&!cache.containsKey(oldest),"Oldest undo evicted at the player limit");
  check(bytesField.getInt(null)==64*BlueprintUndo.MAX_PLAYERS,"Evicted history releases byte accounting");
  BlueprintUndo.clear();
  for(int i=0;i<8;i++)BlueprintUndo.remember(java.util.UUID.randomUUID(),"minecraft:overworld",List.of(new BlueprintUndo.Copy(List.of(cell),List.of(),BlueprintUndo.MAX_HISTORY_BYTES)));
  check(bytesField.getInt(null)<=BlueprintUndo.MAX_CACHE_BYTES&&cache.size()==BlueprintUndo.MAX_CACHE_BYTES/BlueprintUndo.MAX_HISTORY_BYTES,"Undo cache obeys total RAM budget");
  var id=java.util.UUID.randomUUID();BlueprintUndo.remember(id,"minecraft:overworld",List.of(new BlueprintUndo.Copy(List.of(cell),List.of(),BlueprintUndo.MAX_HISTORY_BYTES+1)));
  check(!cache.containsKey(id),"Oversized operation is not retained");
  BlueprintUndo.remember(id,"minecraft:overworld",List.of(tiny));BlueprintUndo.invalidate(id);check(!cache.containsKey(id),"New successful construction invalidates old undo");
  BlueprintUndo.clear();check(cache.isEmpty()&&bytesField.getInt(null)==0&&!BlueprintUndo.tick(),"Server cleanup drops history and idle undo performs no work");
 }
 static void palette(){
  var cells=List.of(new BlueprintPlan.Cell(new BlockPos(0,0,0),Blocks.STONE.defaultBlockState(),null),new BlueprintPlan.Cell(new BlockPos(2,1,3),Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.DOUBLE),null));
  var p=plan(cells);CompoundTag tag=p.save();var loaded=BlueprintPlan.load(tag);
  check(loaded!=null&&loaded.name().equals(p.name())&&loaded.cells().size()==2,"Palette survives save/load with accented plan name");
  check(loaded.cells().get(1).state()==cells.get(1).state(),"Blockstate properties survive palette encoding");
  check(p.materials(6).get(Items.STONE)==6&&p.materials(6).get(Items.OAK_SLAB)==12,"Repeat materials count double slabs correctly");
  check(BlueprintPlan.validSize(new BlockPos(1,1,20)),"Long narrow road/device selection is allowed");
  check(BlueprintPlan.validSize(new BlockPos(32,4,32)),"Selection retains the 4096-cell scan budget");
  check(!BlueprintPlan.validSize(new BlockPos(32,5,32)),"Large sparse cubes cannot expand scan work");
  var longTag=tag.copy();longTag.putLong("Size",new BlockPos(32,4,32).asLong());
  check(BlueprintPlan.load(longTag)!=null,"32-edge plans survive saved-data validation");
  var bad=tag.copy();bad.putLong("Size",new BlockPos(33,1,1).asLong());check(BlueprintPlan.load(bad)==null,"Oversized payload rejected");
  bad=tag.copy();var list=bad.getList("Cells",Tag.TAG_COMPOUND);list.add(list.getCompound(0).copy());check(BlueprintPlan.load(bad)==null,"Duplicate saved coordinates rejected");
  bad=tag.copy();bad.getList("Cells",Tag.TAG_COMPOUND).getCompound(0).putInt("S",999);check(BlueprintPlan.load(bad)==null,"Invalid palette references rejected");
  bad=tag.copy();bad.getList("Cells",Tag.TAG_COMPOUND).getCompound(0).putLong("P",new BlockPos(-1,0,0).asLong());check(BlueprintPlan.load(bad)==null,"Out-of-bounds coordinates rejected");
  bad=tag.copy();list=bad.getList("Cells",Tag.TAG_COMPOUND);while(list.size()<=BlueprintPlan.MAX_BLOCKS)list.add(list.getCompound(0).copy());check(BlueprintPlan.load(bad)==null,"Excessive block counts rejected before decoding entries");
  check(!BlueprintPlan.allowedData(new net.minecraft.world.level.block.entity.ChestBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState())),"Container data cannot be copied");
  check(BlueprintPlan.cost(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER))==0,"Door upper half is not charged twice");
 }
 static void repeatGaps(){
  var stone=Blocks.STONE.defaultBlockState();
  check(BlueprintPlan.repeatStep(List.of(new BlueprintPlan.Cell(BlockPos.ZERO,stone,null)),Direction.EAST,4)==5,"A four-block gap after a one-block object uses a five-block step");
  for(int width=1;width<=8;width++)for(int depth=1;depth<=8;depth++)for(int turns=0;turns<4;turns++){
   // Leading empty selection cells must not count as part of the occupied width.
   var original=List.of(new BlueprintPlan.Cell(new BlockPos(2,0,3),stone,null),new BlueprintPlan.Cell(new BlockPos(width+1,1,depth+2),stone,null));
   var cells=plan(original).rotated(turns);
   for(Direction d:Direction.values())for(int gap:new int[]{0,1,4,8,32}){
    int step=BlueprintPlan.repeatStep(cells,d,gap),min0=Integer.MAX_VALUE,max0=Integer.MIN_VALUE,min1=Integer.MAX_VALUE,max1=Integer.MIN_VALUE;
    for(var c:cells){var p=c.pos();var q=p.relative(d,step);int x=d.getAxis().choose(p.getX(),p.getY(),p.getZ()),y=d.getAxis().choose(q.getX(),q.getY(),q.getZ());min0=Math.min(min0,x);max0=Math.max(max0,x);min1=Math.min(min1,y);max1=Math.max(max1,y);}
    int actual=d.getAxisDirection()==Direction.AxisDirection.POSITIVE?min1-max0-1:min0-max1-1;
    check(actual==gap,"Empty block count matches the slider after rotation, in both positive and negative directions");
   }
  }
 }
 static void coordinates(){
  for(int x=1;x<=16;x++)for(int z=1;z<=16;z++)for(int turns=0;turns<4;turns++){
   BlockPos size=new BlockPos(x,16,z);Set<BlockPos> seen=new HashSet<>();
   for(int px=0;px<x;px++)for(int pz=0;pz<z;pz++){
    BlockPos old=new BlockPos(px,7,pz),next=BlueprintPlan.rotate(old,size,turns);BlockPos nextSize=turns%2==0?size:new BlockPos(z,16,x);
    check(BlueprintPlan.inside(next,nextSize)&&seen.add(next),"Rotation stays within normalized bounds without collisions");
    check(BlueprintPlan.rotate(next,nextSize,4-turns).equals(old),"Inverse rotation restores coordinates");
   }
  }
 }
 static void links(){
  var p=plan(List.of(new BlueprintPlan.Cell(new BlockPos(2,1,3),Blocks.STONE.defaultBlockState(),null)));
  CompoundTag original=new CompoundTag();var list=new ListTag();list.add(location(p.source().offset(2,1,3),p.dimension()));list.add(location(p.source().offset(15,1,15),p.dimension()));list.add(location(p.source().offset(2,1,3),"minecraft:the_nether"));original.put("LinkedTrafficLights",list);original.put("linkedTo",location(BlockPos.ZERO,p.dimension()));original.putString("text","Şehir Merkezi");
  for(int turns=0;turns<4;turns++)for(int copy=0;copy<32;copy++){
   BlockPos anchor=new BlockPos(200+copy*8,70,300);var layout=p.layout(turns);
   var mapped=BlueprintPlan.remap(original,p,layout.destinations(),anchor,turns,"minecraft:the_end");var retained=mapped.getList("LinkedTrafficLights",Tag.TAG_COMPOUND);
   check(retained.size()==1&&!mapped.contains("linkedTo"),"External and cross-dimension device links are detached");
   var target=retained.getCompound(0);var expected=BlueprintPlan.rotate(new BlockPos(2,1,3),p.size(),turns).offset(anchor);
   check(target.getDouble("x")==expected.getX()&&target.getDouble("z")==expected.getZ()&&target.getString("dimension").equals("minecraft:the_end")&&target.getInt("SignalFace")==1,"Each repeated copy targets its own rotated internal device face");
   check(mapped.getString("text").equals("Şehir Merkezi"),"Sign text survives link remapping");
  }
  check(original.getList("LinkedTrafficLights",Tag.TAG_COMPOUND).size()==3&&original.contains("linkedTo"),"Remapping never mutates the saved original");
 }
 static void assemblies(Block[] b){
  for(Direction facing:Direction.Plane.HORIZONTAL){
   List<BlueprintPlan.Cell> cells=new ArrayList<>();BlockPos root=new BlockPos(6,2,6);
   for(int i=0;i<CurvedTrafficPoleBlock.partCount();i++){var state=b[0].defaultBlockState().setValue(CurvedTrafficPoleBlock.FACING,facing).setValue(CurvedTrafficPoleBlock.PART,i);cells.add(new BlueprintPlan.Cell(CurvedTrafficPoleBlock.partPos(root,facing,i),state,null));check(BlueprintPlan.cost(state)==(i==0?1:0),"Curved assembly charges one item");}
   check(BlueprintPlacement.complete(cells),"Complete curved assembly accepted");
   for(int turns=0;turns<4;turns++)check(BlueprintPlacement.complete(plan(cells).rotated(turns)),"Rotated curved parts retain a matching master");
   cells.remove(2);check(!BlueprintPlacement.complete(cells),"Incomplete curved assembly rejected");
  }
  for(int kind:new int[]{1,2})for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean mounted:new boolean[]{false,true})for(boolean hanging:new boolean[]{false,true}){
   var led=(LedDeviceBlock)b[kind];BlockPos root=new BlockPos(6,5,6);var state=led.defaultBlockState().setValue(LedDeviceBlock.FACING,facing).setValue(LedDeviceBlock.POST_MOUNTED,mounted).setValue(LedDeviceBlock.POST_ABOVE,hanging);
   List<BlueprintPlan.Cell> cells=new ArrayList<>();cells.add(new BlueprintPlan.Cell(root,state,new CompoundTag()));
   Direction width=facing.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.SOUTH;
   for(BlockPos part:led.blueprintParts(root,state)){int y=part.getY()-root.getY(),offset=(part.getX()-root.getX())*width.getStepX()+(part.getZ()-root.getZ())*width.getStepZ();var helper=b[3].defaultBlockState().setValue(LedDevicePartBlock.FACING,facing).setValue(LedDevicePartBlock.VERTICAL_OFFSET,y+1).setValue(LedDevicePartBlock.UPPER,y!=0).setValue(LedDevicePartBlock.OFFSET,offset+1).setValue(LedDevicePartBlock.ROW,0);cells.add(new BlueprintPlan.Cell(part,helper,null));}
   check(BlueprintPlacement.complete(cells),"Complete display assembly accepted");
   for(int turns=0;turns<4;turns++){
    var p=plan(cells);var layout=p.layout(turns);check(BlueprintPlacement.complete(layout.cells()),"Every display rotation retains a valid anchored footprint");
    Set<BlockPos> expected=new HashSet<>();for(var c:cells)expected.add(BlueprintPlan.rotate(c.pos(),p.size(),turns));Set<BlockPos> actual=new HashSet<>();for(var c:layout.cells())actual.add(c.pos());check(actual.equals(expected),"Display rotation neither adds cells nor loses occupied cells");
    var main=layout.cells().stream().filter(c->c.state().getBlock()==led).findFirst().orElseThrow();check(layout.destinations().get(root).equals(main.pos()),"Display internal links follow the shifted master cell");
   }
   cells.remove(cells.size()-1);check(!BlueprintPlacement.complete(cells),"Incomplete display is rejected");
  }
  var post=b[4].defaultBlockState().setValue(TrafficSignPostBlock.NORTH,true).setValue(TrafficSignPostBlock.CEILING_SUPPORT,true);
  var turned=BlueprintPlan.rotateState(post,1);check(turned.getValue(TrafficSignPostBlock.EAST)&&!turned.getValue(TrafficSignPostBlock.NORTH)&&turned.getValue(TrafficSignPostBlock.CEILING_SUPPORT),"Pole arms rotate while ceiling mounting survives");
 }
 static void preview()throws Exception {
  var type=com.destan.trafficengine.client.BlueprintPreview.class;
  var rebuild=type.getDeclaredMethod("rebuild",CompoundTag.class);rebuild.setAccessible(true);
  var ghostField=type.getDeclaredField("ghosts");ghostField.setAccessible(true);var meshField=type.getDeclaredField("MESHES");meshField.setAccessible(true);
  var anchorField=type.getDeclaredField("anchor");anchorField.setAccessible(true);var dirtyField=type.getDeclaredField("dirty");dirtyField.setAccessible(true);
  var p=plan(List.of(new BlueprintPlan.Cell(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null),new BlueprintPlan.Cell(new BlockPos(0,1,0),Blocks.STONE.defaultBlockState(),null)));
  var data=new CompoundTag();data.put("Plan0",p.save());data.putBoolean("Placing",true);data.putInt("Copies",32);data.putInt("Spacing",4);data.putInt("Direction",Direction.EAST.get2DDataValue());
  rebuild.invoke(null,data);var ghosts=(List<?>)ghostField.get(null);check(ghosts.size()==64,"Thirty-two copies use a bounded cached preview");check(((Map<?,?>)meshField.get(null)).size()==1,"All repeated stone cells share one wire mesh");
  var relative=ghosts.get(0).getClass().getDeclaredMethod("relative");relative.setAccessible(true);
  var first=(BlockPos)relative.invoke(ghosts.get(0));var second=(BlockPos)relative.invoke(ghosts.get(2));
  check(second.getX()-first.getX()-1==4,"Rendered preview leaves four empty blocks for Gap 4");
  var edges=ghosts.get(0).getClass().getDeclaredMethod("edges");edges.setAccessible(true);Object mesh=edges.invoke(ghosts.get(0));for(Object g:ghosts)check(edges.invoke(g)==mesh,"Repeat instances reuse the same edge array");
  anchorField.set(null,new BlockPos(100,64,100));dirtyField.setBoolean(null,false);
  com.destan.trafficengine.client.BlueprintPreview.invalidate(new BlockPos(500,64,500));check(!dirtyField.getBoolean(null),"Unrelated world edits do not invalidate preview");
  com.destan.trafficengine.client.BlueprintPreview.invalidate(new BlockPos(100,64,100));check(dirtyField.getBoolean(null),"Relevant world edits invalidate conflict colors");
  com.destan.trafficengine.client.BlueprintPreview.clear();check(((List<?>)ghostField.get(null)).isEmpty()&&((Map<?,?>)meshField.get(null)).isEmpty(),"Resource reload/tool switch releases preview geometry");
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();long start=System.nanoTime();
  simplifiedToolAndUndo();palette();repeatGaps();coordinates();links();assemblies(fixtures());preview();
  System.out.println("PASS Blueprint: "+checks+" assertions; palette validation, 32 repeat offsets, material costs, four rotations, all display footprints, internal links and container exclusion ("+((System.nanoTime()-start)/1_000_000)+" ms; headless core check, not FPS benchmark)");
 }
}
