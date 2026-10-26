import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.block.entity.*;
import com.destan.trafficengine.data.*;
import com.destan.trafficengine.util.SignalFaces;
import com.destan.trafficengine.client.gui.MenuTextBounds;
import com.destan.trafficengine.mixin.*;
import com.destan.trafficengine.network.*;
import com.destan.trafficengine.network.packets.cts.TrafficLightPacket;
import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Native Minecraft classes, no game window, packaged mod JAR or loader stubs. */
public final class Update20RegressionCheck {
    static int checks;
    static void bindIcon(Object entry,Object icon)throws Exception{
        Field value=entry.getClass().getDeclaredField("value");value.setAccessible(true);
        value.set(entry,Proxy.newProxyInstance(entry.getClass().getClassLoader(),new Class<?>[]{dev.architectury.registry.registries.RegistrySupplier.class},(proxy,method,args)->method.getName().equals("get")?icon:method.getName().equals("isPresent")?Boolean.TRUE:null));
    }
    static void headlessIcons()throws Exception{
        // These four enum icons are normally supplied by loader registration. Only their visuals are fixtures.
        bindIcon(com.destan.trafficengine.registry.ModBlocks.TRAFFIC_LIGHT_REQUEST_BUTTON,Blocks.STONE);
        bindIcon(com.destan.trafficengine.registry.ModBlocks.TRAFFIC_LIGHT,Blocks.STONE);
        bindIcon(com.destan.trafficengine.registry.ModBlocks.TRAFFIC_LIGHT_CONTROLLER,Blocks.STONE);
        bindIcon(com.destan.trafficengine.registry.ModItems.CREATIVE_PATTERN_CATALOGUE,net.minecraft.world.item.Items.PAPER);
    }
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static boolean green(TrafficLightSchedule.PhaseState p){return p.colors().contains(TrafficLightColor.GREEN);}
    static boolean yellow(TrafficLightSchedule.PhaseState p){return p.colors().contains(TrafficLightColor.YELLOW);}
    static TrafficLightSchedule schedule(int ids,boolean manual,TrafficLightSchedule.TransitionLight incoming){
        var s=new TrafficLightSchedule();
        for(int id=1;id<=ids;id++){
            var e=new TrafficLightScheduleEntryData();e.setPhaseId(id);e.setDurationTicks(80+id*20);e.enableOnlyColors(List.of(TrafficLightColor.GREEN));s.getEntries().add(e);
        }
        s.convertToSequentialGreens();s.setRedYellowSeconds(3);s.setTransitionLight(incoming);s.setManualController(manual);return s;
    }
    static void schedules(){
        for(var transition:TrafficLightSchedule.TransitionLight.values())for(int n=1;n<=4;n++)for(boolean manual:new boolean[]{false,true}){
            var s=schedule(n,manual,transition);int cycle=s.getSequentialCycleTicks();
            for(int id=1;id<=n;id++){
                int greens=0,outgoing=0,incoming=0;boolean sawDirectRedGreen=false;
                for(int t=0;t<cycle;t++){
                    var p=s.getSequentialPhaseAt(id,false,t);var previous=s.getSequentialPhaseAt(id,false,t-1);
                    check(p.remainingTicks()>0&&p.durationTicks()>0,"No zero-duration or stalled phase");
                    check(p.colors().equals(s.getSequentialPhaseAt(id,false,t+cycle).colors()),"Stable phase across cycle boundary");
                    if(green(p)){greens++;if(previous.colors().equals(List.of(TrafficLightColor.RED)))sawDirectRedGreen=true;}
                    if(yellow(p)){if(p.colors().contains(TrafficLightColor.RED))incoming++;else outgoing++;}
                    if(green(previous)&&!green(p))check(p.colors().equals(List.of(TrafficLightColor.YELLOW)),"Green always exits through yellow");
                    if(green(p)||yellow(p))check(!green(s.getSequentialPhaseAt(id,true,t)),"Crossing cannot open during vehicle green/yellow");
                }
                check(greens==80+id*20,"Configured green duration retained");
                check(outgoing==(transition==TrafficLightSchedule.TransitionLight.YELLOW?120:60),"Outgoing yellow retained when None selected");
                check(incoming==(transition==TrafficLightSchedule.TransitionLight.RED_YELLOW?60:0),"Incoming transition type respected");
                check(sawDirectRedGreen==(transition==TrafficLightSchedule.TransitionLight.NONE),"None skips only red-to-green transition");
            }
            for(int t=0;t<cycle;t++){
                int active=0;for(int id=1;id<=n;id++)if(green(s.getSequentialPhaseAt(id,false,t)))active++;
                check(active<=1,"No conflicting vehicle greens");
            }
            var copied=s.copy();check(copied.getIncomingTransitionTicks()==s.getIncomingTransitionTicks(),"Copy retains transition selection");
            var loaded=new TrafficLightSchedule();loaded.fromNbt(s.toNbt());check(loaded.getTransitionLight()==transition&&loaded.getRedYellowTicks()==60,"NBT roundtrip");
            var b=new FriendlyByteBuf(Unpooled.buffer());try{s.toBytes(b);var wire=TrafficLightSchedule.fromBytes(b);check(wire.getTransitionLight()==transition&&wire.getRedYellowTicks()==60,"Wire roundtrip");}finally{b.release();}
        }
        var requested=schedule(1,false,TrafficLightSchedule.TransitionLight.NONE);requested.setTrigger(TrafficLightTrigger.ON_REQUEST);
        check(yellow(requested.getSequentialPhaseAt(1,false,requested.getSequentialStartTick())),"Request begins at outgoing yellow");
        check(green(requested.getSequentialPhaseAt(1,false,requested.getSequentialEndTick())),"Request returns to through green");
        boolean crossing=false;for(int t=requested.getSequentialStartTick();t<requested.getSequentialEndTick();t++)crossing|=green(requested.getSequentialPhaseAt(1,true,t));
        check(crossing,"Requested crossing still has a green interval");
        var old=requested.toNbt();old.remove("incomingTransitionEnabled");old.putInt("redYellowTicks",0);var migration=new TrafficLightSchedule();migration.fromNbt(old);
        check(migration.getTransitionLight()==TrafficLightSchedule.TransitionLight.NONE&&migration.getRedYellowTicks()==40,"Legacy None gains outgoing yellow without incoming yellow");
        var b=new FriendlyByteBuf(Unpooled.buffer());try{requested.toBytes(b);b.setInt(5,0);var wire=TrafficLightSchedule.fromBytes(b);check(wire.getIncomingTransitionTicks()==0&&wire.getRedYellowTicks()==40,"Legacy zero wire duration migrated");}finally{b.release();}
    }
    static void symbols(){
        var pattern=PixelPattern.of(new long[]{3,4,8,16});var other=PixelPattern.of(new long[]{2,0,0,0});
        var s=SignalSymbols.DEFAULT.with(0,TrafficLightIcon.RIGHT,PixelPattern.EMPTY).with(1,TrafficLightIcon.NONE,pattern).with(3,TrafficLightIcon.NONE,other);
        check(SignalSymbols.DEFAULT.inherits(0),"Immutable default symbols");
        check(s.icon(0,TrafficLightIcon.LEFT)==TrafficLightIcon.RIGHT&&s.mask(1,PixelPattern.EMPTY).equals(pattern)&&s.mask(3,pattern).equals(other),"Four slots have independent masks/icons");
        check(s.icon(2,TrafficLightIcon.BIKE)==TrafficLightIcon.BIKE&&s.mask(2,pattern).equals(pattern),"Unedited bulbs inherit global settings");
        check(s.with(1,null,other).mask(1,pattern).equals(pattern),"Reset restores inheritance");
        check(s.with(-1,null,null)==s&&s.with(4,null,null)==s,"Invalid slot cannot allocate or edit");
        CompoundTag tag=new CompoundTag();s.write(tag);check(SignalSymbols.read(tag).equals(s),"Symbol NBT roundtrip");
        ListTag bad=new ListTag();CompoundTag e=new CompoundTag();e.putByte("slot",(byte)127);e.putLongArray("pixels",new long[64]);bad.add(e);tag.put("SignalSymbols",bad);
        check(SignalSymbols.read(tag).equals(SignalSymbols.DEFAULT),"Malformed symbol slots ignored");
        var p=new TrafficLightPacket(BlockPos.ZERO,List.of(TrafficLightColor.GREEN),TrafficLightType.NOCOUNTDOWN,TrafficLightModel.THREE_LIGHTS,TrafficLightIcon.NONE,TrafficLightControlType.REMOTE,new TrafficLightColor[]{TrafficLightColor.RED,TrafficLightColor.YELLOW,TrafficLightColor.GREEN},12,List.of(),true).withFace(1).withSymbols(s).withMask(pattern);
        var p2=new TrafficLightPacket(PacketStatus.OK);p2.deserializeNbt(p.serializeNbt());
        check(p.serializeNbt().equals(p2.serializeNbt()),"Packet roundtrip retains face and per-bulb drawing");
    }
    static Object[] equipment()throws Exception{
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object previous=holders.get(BuiltInRegistries.BLOCK);boolean was=frozen.getBoolean(BuiltInRegistries.BLOCK);frozen.setBoolean(BuiltInRegistries.BLOCK,false);holders.set(BuiltInRegistries.BLOCK,new IdentityHashMap<>());
        try{return new Object[]{new DoubleTrafficLightBlock(),new ChevronPanelBlock(),new PedestrianRailingBlock(),new TrafficPoleBaseBlock(),new SidewalkSlopeBlock()};}
        finally{holders.set(BuiltInRegistries.BLOCK,previous);frozen.setBoolean(BuiltInRegistries.BLOCK,was);}
    }
    static void geometry(Object[] eq){
        var light=(DoubleTrafficLightBlock)eq[0];var sign=(ChevronPanelBlock)eq[1];var rail=(PedestrianRailingBlock)eq[2];var base=(TrafficPoleBaseBlock)eq[3];var slope=(SidewalkSlopeBlock)eq[4];
        check(rail.defaultBlockState().getValue(PedestrianRailingBlock.GRAY),"Gray railing is the placement default");
        Set<VoxelShape> shapes=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var front:TrafficLightModel.values())for(var back:TrafficLightModel.values())for(var dir:Direction.Plane.HORIZONTAL)for(boolean diagonal:new boolean[]{false,true}){
            var state=light.defaultBlockState().setValue(TrafficLightBlock.MODEL,front).setValue(DoubleTrafficLightBlock.BACK_MODEL,back).setValue(TrafficLightBlock.FACING,dir).setValue(TrafficLightBlock.DIAGONAL,diagonal);
            var a=light.getShape(state,null,BlockPos.ZERO,CollisionContext.empty());shapes.add(a);check(a==light.getShape(state,null,BlockPos.ZERO,CollisionContext.empty()),"Double shapes reused");
            double dx=dir.getStepX(),dz=dir.getStepZ();if(diagonal){double x=dx;dx=(dx-dz)*Math.sqrt(.5);dz=(x+dz)*Math.sqrt(.5);}
            check(SignalFaces.hit(state,BlockPos.ZERO,new Vec3(.5+dx*.4,.7,.5+dz*.4))==0&&SignalFaces.hit(state,BlockPos.ZERO,new Vec3(.5-dx*.4,.7,.5-dz*.4))==1,"Front/back picking matches cardinal and diagonal meshes");
        }
        check(shapes.size()<=128,"Double geometry cache has a finite upper bound");
        for(var s:sign.getStateDefinition().getPossibleStates()){
            check(sign.getOcclusionShape(s,null,BlockPos.ZERO).isEmpty(),"Detailed diagonal geometry never projected for occlusion");
            check(s.getLightEmission()==7,"Existing sign brightness retained");
            var a=sign.getShape(s,null,BlockPos.ZERO,CollisionContext.empty());check(a==sign.getShape(s,null,BlockPos.ZERO,CollisionContext.empty()),"Diagonal sign reuses its shape");
        }
        for(int layers=1;layers<=8;layers++){
            var terrain=slope.defaultBlockState().setValue(BlockStateProperties.LAYERS,layers);int sink=TrafficPoleBaseBlock.sinkFor(terrain);check(sink==9-layers,"Footing depth follows terrain layers");
            var s=base.defaultBlockState().setValue(TrafficPoleBaseBlock.SINK,sink);var a=base.getShape(s,null,BlockPos.ZERO,CollisionContext.empty());
            check(a.min(Direction.Axis.Y)==-2.0*sink/16&&a.max(Direction.Axis.Y)==1,"Buried footing extends into terrain and meets upper pole");
            check(a==base.getShape(s.cycle(TrafficPoleBaseBlock.CONNECTED),null,BlockPos.ZERO,CollisionContext.empty()),"Buried base shares connected shape");
        }
        check(TrafficPoleBaseBlock.sinkFor(Blocks.STONE.defaultBlockState())==0,"Full ground uses original footing");
        Set<VoxelShape> baseShapes=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var state:base.getStateDefinition().getPossibleStates()){
            var shape=base.getShape(state,null,BlockPos.ZERO,CollisionContext.empty());baseShapes.add(shape);
            check(shape.max(Direction.Axis.Y)==1,"Standalone footing includes the inventory pole through the top of its block");
            check(shape==base.getCollisionShape(state,null,BlockPos.ZERO,CollisionContext.empty()),"Selection and collision match the integrated pole");
            check(shape==base.getShape(state.cycle(TrafficPoleBaseBlock.CONNECTED).cycle(TrafficPoleBaseBlock.WATERLOGGED),null,BlockPos.ZERO,CollisionContext.empty()),"Adding or removing an upper pole cannot remove the base's own pole");
        }
        check(baseShapes.size()==9,"Footing geometry stays cached and bounded to the nine terrain depths");

    }
    static void independentFaces(Object[] eq)throws Exception{
        var block=(DoubleTrafficLightBlock)eq[0];var state=block.defaultBlockState().setValue(TrafficLightBlock.MODEL,TrafficLightModel.ONE_LIGHT).setValue(DoubleTrafficLightBlock.BACK_MODEL,TrafficLightModel.FOUR_LIGHTS);
        var registry=BuiltInRegistries.BLOCK_ENTITY_TYPE;var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object oldHolders=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);holders.set(registry,new IdentityHashMap<>());frozen.setBoolean(registry,false);
        BlockEntityType<?> type;try{type=BlockEntityType.Builder.of(TrafficLightBlockEntity::create,block).build(null);}finally{holders.set(registry,oldHolders);frozen.setBoolean(registry,wasFrozen);}
        bindIcon(com.destan.trafficengine.registry.ModBlockEntities.TRAFFIC_LIGHT_BLOCK_ENTITY,type);
        check(type.create(BlockPos.ZERO,state) instanceof DoubleTrafficLightBlockEntity,"Registered type factory reconstructs a double signal when chunks reload");
        var ctor=DoubleTrafficLightBlockEntity.class.getDeclaredConstructor(BlockEntityType.class,BlockPos.class,BlockState.class);ctor.setAccessible(true);
        var light=ctor.newInstance(type,new BlockPos(2,4,6),state);var rear=light.face(1);
        check(light.face(0)==light&&rear!=light&&rear.getSignalFace()==1,"Separate owned signal data with one registered owner");
        light.setPhaseId(11);rear.setPhaseId(23);light.setIcon(TrafficLightIcon.RIGHT);rear.setIcon(TrafficLightIcon.BIKE);
        light.setSignalSymbols(SignalSymbols.DEFAULT.with(0,TrafficLightIcon.LEFT,PixelPattern.EMPTY));rear.setSignalSymbols(SignalSymbols.DEFAULT.with(2,TrafficLightIcon.NONE,PixelPattern.of(new long[]{1,2,3,4})));
        light.setSchedule(schedule(1,false,TrafficLightSchedule.TransitionLight.NONE));rear.setSchedule(schedule(2,false,TrafficLightSchedule.TransitionLight.RED_YELLOW));
        check(light.getPhaseId()==11&&rear.getPhaseId()==23&&light.getIcon()!=rear.getIcon(),"Configuring rear cannot alter front");
        check(rear.getBlockState().getValue(TrafficLightBlock.MODEL)==TrafficLightModel.FOUR_LIGHTS&&rear.getBlockState().getValue(TrafficLightBlock.FACING)==state.getValue(TrafficLightBlock.FACING).getOpposite(),"Rear has its own lamp count and orientation");
        check(rear.getBlockState()==rear.getBlockState(),"Rear state is cached between actual block state changes");
        check(!light.getTimingKey().equals(rear.getTimingKey()),"Countdown caches cannot cross faces");
        CompoundTag saved;saved=(CompoundTag)light.getClass().getMethod("getUpdateTag").invoke(light);
        var loaded=ctor.newInstance(type,new BlockPos(2,4,6),state);
        loaded.getClass().getMethod("load",CompoundTag.class).invoke(loaded,saved);
        check(loaded.getPhaseId()==11&&loaded.face(1).getPhaseId()==23,"Two IDs survive world NBT reload");
        check(loaded.getSignalSymbols().equals(light.getSignalSymbols())&&loaded.face(1).getSignalSymbols().equals(rear.getSignalSymbols()),"Two symbol configurations survive world NBT reload");
        check(loaded.getSchedule().getTransitionLight()==TrafficLightSchedule.TransitionLight.NONE&&loaded.face(1).getSchedule().getEntries().size()==2,"Schedules persist independently");
        loaded.setRemoved();check(loaded.isRemoved()&&loaded.face(1).isRemoved(),"Removal propagates to owned rear");loaded.clearRemoved();check(!loaded.isRemoved()&&!loaded.face(1).isRemoved(),"Chunk revival propagates to owned rear");
        var dimension=Style.DEFAULT_FONT;var front=new WorldLocation(2,4,6,dimension);var back=front.withSignalFace(1);check(!front.equals(back)&&new HashSet<>(List.of(front,back)).size()==2,"Two controller links at one coordinate remain distinct");
        check(back.equals(WorldLocation.loadFromNbt(back.toNbt()))&&front.equals(WorldLocation.loadFromNbt(front.toNbt())),"Legacy links default to front; rear links roundtrip");
        check(front.getLocationBlockPos()==front.getLocationBlockPos(),"Controller lookup reuses immutable coordinate");
    }
    static float glyphOrigin;
    static final class TestGlyph extends BakedGlyph implements MenuGlyphAccessor{
        final float top,bottom;TestGlyph(float t,float b){super(null,0,1,0,1,0,7,t,b);top=t;bottom=b;}
        public float trafficengine$top(){return top;}public float trafficengine$bottom(){return bottom;}
    }
    static final class TestInfo implements com.mojang.blaze3d.font.GlyphInfo, com.destan.trafficengine.client.gui.MenuGlyphInk {
        final int code; TestInfo(int code){this.code=code;}
        public float getAdvance(){return 6;}
        public BakedGlyph bake(java.util.function.Function<com.mojang.blaze3d.font.SheetGlyphInfo,BakedGlyph> baker){return null;}
        public Ink trafficengine$ink(){return new Ink(code=='Ö'?-2:0,code=='Ç'?9:7);}
    }
    static final class TestSet extends FontSet{
        int reads;TestSet(){super(null,Style.DEFAULT_FONT);}
        @Override public com.mojang.blaze3d.font.GlyphInfo getGlyphInfo(int code,boolean filter){reads++;return new TestInfo(code);}
        @Override public BakedGlyph getGlyph(int code){reads++;return new TestGlyph(glyphOrigin+(code=='Ö'?-2:code=='Ç'?0:0),glyphOrigin+(code=='Ç'?9:7));}
    }
    static final class TestFont extends Font implements MenuFontAccessor{
        TestSet set;int widths;TestFont(TestSet s){super(key->s,false);set=s;}
        public FontSet trafficengine$fontSet(ResourceLocation key){return set;}
        @Override public int width(FormattedCharSequence text){widths++;int[] n={0};text.accept((i,s,c)->{n[0]+=6;return true;});return n[0];}
    }
    static void textMetrics()throws Exception{
        Field origin=MenuTextBounds.class.getDeclaredField("GLYPH_ORIGIN");origin.setAccessible(true);glyphOrigin=origin.getFloat(null);
        TestSet set=new TestSet();TestFont font=new TestFont(set);
        for(String caption:new String[]{"Done","Özel","GEÇ","Rückseite","Straßenbeleuchtung","特殊符号"}){
            var bounds=MenuTextBounds.of(font,caption);int reads=set.reads,widths=font.widths;
            for(int i=0;i<100;i++)check(MenuTextBounds.of(font,caption)==bounds,"Caption bounds reused between frames");
            check(reads==set.reads&&widths==font.widths,"No repeated glyph or width measurements");
            for(float scale:new float[]{.75f,.9f,1f,1.1111112f,1.25f}){
                int y=MenuTextBounds.centered(30,22,scale,bounds);double center=y+(bounds.top()+bounds.bottom())*.5*scale;check(Math.abs(center-41)<=.51,"Displayed accents centered within pixel rounding");
            }
        }
        String key="Özel";var first=MenuTextBounds.of(font,key);MenuTextBounds.invalidate();check(MenuTextBounds.of(font,key)!=first,"Font/language reload invalidates measurements");
        var before=MenuTextBounds.of(font,key);font.set=new TestSet();check(MenuTextBounds.of(font,key)!=before,"Font-set replacement invalidates measurements");
        for(int i=0;i<1100;i++)MenuTextBounds.of(font,"unique_"+i);
        Field cache=MenuTextBounds.class.getDeclaredField("CACHE");cache.setAccessible(true);check(((Map<?,?>)cache.get(null)).size()<=1024,"Menu metric cache is bounded");
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        headlessIcons();
        schedules();symbols();Object[] eq=equipment();geometry(eq);independentFaces(eq);textMetrics();
        System.out.println("PASS: "+checks+" assertions: phase safety/legacy data, four independent bulb symbols, front/back persistence/link identity, cached geometry, buried footing, gray railing and localized cached glyph bounds.");
    }
}
