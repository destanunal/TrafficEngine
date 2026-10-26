import com.destan.trafficengine.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class CurvedPoleCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static final class World implements BlockGetter {
        int reads;
        public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Shape lookup queried a block entity");}
        public BlockState getBlockState(BlockPos p){reads++;return Blocks.AIR.defaultBlockState();}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public int getHeight(){return 384;}
        public int getMinBuildHeight(){return -64;}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);
        CurvedTrafficPoleBlock pole;TrafficLightBlock lamp;HorizontalTrafficLightBlock horizontal;DoubleTrafficLightBlock doubleLamp;TrafficSignPostBlock post;TrafficSignBlock sign;RoadInformationSignBlock speed;LedDeviceBlock led;
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        long start=System.nanoTime();
        try{pole=new CurvedTrafficPoleBlock();lamp=new TrafficLightBlock();horizontal=new HorizontalTrafficLightBlock();doubleLamp=new DoubleTrafficLightBlock();post=new TrafficSignPostBlock();sign=new TrafficSignBlock();speed=new RoadInformationSignBlock(RoadInformationSignBlock.Kind.VARIABLE_SPEED);led=new LedDeviceBlock(com.destan.trafficengine.block.data.LedDeviceType.LED_LIGHT);}
        finally{holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        long cold=System.nanoTime()-start;
        World world=new World();BlockPos origin=new BlockPos(15,65,-16);
        Set<VoxelShape> identities=Collections.newSetFromMap(new IdentityHashMap<>());
        for(BlockState state:pole.getStateDefinition().getPossibleStates()) {
            var shape=state.getShape(world,origin);identities.add(shape);
            check(shape==state.getShape(world,origin),"Outline is shared");
            check(!shape.isEmpty(),"Every occupied cell has a selection/collision shape");
            var bounds=shape.bounds();
            check(bounds.minX>=0&&bounds.minY>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxY<=1&&bounds.maxZ<=1,"Each shape stays inside its own rendered cell");
            check(!state.hasBlockEntity(),"No block entity or ticker");
            int part=state.getValue(CurvedTrafficPoleBlock.PART);Direction facing=state.getValue(CurvedTrafficPoleBlock.FACING);
            BlockPos pos=CurvedTrafficPoleBlock.partPos(origin,facing,part);
            check(CurvedTrafficPoleBlock.masterPos(pos,state).equals(origin),"Owner/cell coordinates round-trip at chunk boundaries");
            boolean port=part==0||part==CurvedTrafficPoleBlock.partCount()-1;
            for(Direction side:Direction.values()) {
                boolean expected=port&&side==Direction.DOWN||part==5&&side.getAxis()==facing.getAxis();
                check(pole.canConnect(state,side)==expected,"Only the lower sockets and horizontal endpoint connect to posts");
                check(pole.canAttach(state,origin,side)==(port&&side==Direction.DOWN),"Lamp mounting remains on downward sockets");
            }
            if(part==5&&facing==Direction.NORTH&&state.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT)) {
                check(Math.abs(bounds.minZ)<1e-8,"Horizontal socket reaches the adjacent post cell without a gap");
                check(shape.toAabbs().stream().anyMatch(b->b.minZ==0&&Math.abs(b.minY-7.0/16)<1e-8&&Math.abs(b.maxY-9.0/16)<1e-8),"Arm meets the ordinary post at its exact 7..9 vertical section");
                check(shape.toAabbs().stream().anyMatch(b->b.minY==0&&b.minX<=7.0/16&&b.maxX>=9.0/16&&b.minZ<=7.0/16&&b.maxZ>=9.0/16),"The original downward carrier is retained");
            }
            var wet=state.cycle(CurvedTrafficPoleBlock.WATERLOGGED);
            check(shape==wet.getShape(world,origin),"Water state reuses geometry");
            var turned=pole.rotate(state,Rotation.CLOCKWISE_90);
            check(turned.getValue(CurvedTrafficPoleBlock.FACING)==facing.getClockWise(),"Structure rotation changes arm direction");
            check(turned.getValue(CurvedTrafficPoleBlock.PART)==part,"Structure rotation preserves the part index");
            for(Rotation rotation:Rotation.values()) {
                var rotated=pole.rotate(state,rotation);
                BlockPos offset=pos.subtract(origin).rotate(rotation);
                check(CurvedTrafficPoleBlock.partPos(origin,rotated.getValue(CurvedTrafficPoleBlock.FACING),part).equals(origin.offset(offset)),"Structure rotation preserves the assembled footprint");
            }
        }
        check(identities.size()==28,"Only the terminal cell adds four mounted shapes; all other states share the 24 bare shapes");
        LevelAccessor edits=(LevelAccessor)java.lang.reflect.Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),new Class<?>[]{LevelAccessor.class},(proxy,method,arguments)->{
            if(method.getName().equals("isClientSide"))return true;
            throw new AssertionError("Neighbour update queried the world: "+method.getName());
        });
        check(!pole.defaultBlockState().getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"New bare poles hide the socket");
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            BlockState bare=pole.defaultBlockState().setValue(CurvedTrafficPoleBlock.PART,5).setValue(CurvedTrafficPoleBlock.FACING,facing);
            check(Math.abs(bare.getShape(world,origin).bounds().minY-7.0/16)<1e-8,"Bare arm has no lower collision stem");
            for(Block light:new Block[]{lamp,horizontal,doubleLamp}) {
                BlockState mounted=pole.updateShape(bare,Direction.DOWN,light.defaultBlockState(),edits,origin,origin.below());
                check(mounted.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"Every traffic lamp type enables the socket");
                check(mounted.getShape(world,origin).bounds().minY==0,"Mounted socket reaches the lamp cell");
                check(pole.updateShape(mounted,Direction.DOWN,light.defaultBlockState(),edits,origin,origin.below())==mounted,"Unchanged attachment reuses its exact state");
                check(pole.updateShape(mounted,Direction.DOWN,Blocks.AIR.defaultBlockState(),edits,origin,origin.below())==bare,"Removing a lamp removes the socket and restores bare geometry");
                check(pole.updateShape(mounted,Direction.EAST,Blocks.AIR.defaultBlockState(),edits,origin,origin.east())==mounted,"Side edits preserve the lamp socket");
            }
            check(pole.updateShape(bare,Direction.DOWN,Blocks.STONE.defaultBlockState(),edits,origin,origin.below())==bare,"Other blocks cannot create a lamp socket");
        }
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            BlockState bare=pole.defaultBlockState().setValue(CurvedTrafficPoleBlock.PART,5).setValue(CurvedTrafficPoleBlock.FACING,facing);
            for(BlockState equipment:new BlockState[]{post.defaultBlockState(),sign.defaultBlockState(),speed.defaultBlockState(),led.defaultBlockState().setValue(LedDeviceBlock.POST_ABOVE,true).setValue(LedDeviceBlock.POST_MOUNTED,true)}) {
                BlockState mounted=pole.updateShape(bare,Direction.DOWN,equipment,edits,origin,origin.below());
                check(mounted.getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"New equipment enables the lower mounting socket");
                check(pole.updateShape(mounted,Direction.DOWN,Blocks.AIR.defaultBlockState(),edits,origin,origin.below())==bare,"Removing equipment restores the bare endpoint");
                check(pole.updateShape(mounted,Direction.DOWN,equipment,edits,origin,origin.below())==mounted,"Repeated equipment update returns its existing state");
            }
            for(Direction side:Direction.values()) {
                check(CurvedTrafficPoleBlock.isMountingPost(bare,side)==(side==Direction.DOWN||side.getAxis()==facing.getAxis()),"Endpoint accepts downward and aligned side mounts only");
                check(CurvedTrafficPoleBlock.isMountingPost(post.defaultBlockState(),side),"Standard post compatibility retained");
                check(!CurvedTrafficPoleBlock.isMountingPost(Blocks.AIR.defaultBlockState(),side),"Air does not become a support");
                for(int part=1;part<5;part++)check(!CurvedTrafficPoleBlock.isMountingPost(bare.setValue(CurvedTrafficPoleBlock.PART,part),side),"Empty portions of curve cells are not mounting ports");
            }
            check(!pole.updateShape(bare,Direction.DOWN,led.defaultBlockState().setValue(LedDeviceBlock.WALL_MOUNTED,true),edits,origin,origin.below()).getValue(CurvedTrafficPoleBlock.LAMP_SUPPORT),"Wall LED does not grow a hanging stem");
            check(Math.abs(RoadInformationSignBlock.plateThickness(RoadInformationSignBlock.Kind.HIGHWAY)-.5)<1e-8,"Highway panel is slightly thinner");
            check(RoadInformationSignBlock.plateThickness(RoadInformationSignBlock.Kind.VARIABLE_SPEED)==1,"Electronic display keeps its thickness");
        }
        world.reads=0;start=System.nanoTime();
        for(int pass=0;pass<1000;pass++)for(BlockState state:pole.getStateDefinition().getPossibleStates())state.getShape(world,origin);
        long warm=System.nanoTime()-start;
        check(world.reads==0,"96,000 warm lookups read no world state");
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            Set<BlockPos> occupied=new HashSet<>();
            for(int part=0;part<CurvedTrafficPoleBlock.partCount();part++)check(occupied.add(CurvedTrafficPoleBlock.partPos(origin,facing,part)),"No two parts overwrite the same position");
            for(BlockPos p:occupied)if(!p.equals(origin))check(Direction.stream().anyMatch(d->occupied.contains(p.relative(d))),"Footprint has connected neighbours");
        }
        if(args.length>0)for(String path:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))) {
            String json=java.nio.file.Files.readString(java.nio.file.Path.of(path));
            var parsed=net.minecraft.client.renderer.block.model.BlockModel.fromString(json);
            check(parsed!=null,"Minecraft parses the authored model: "+path);
            if(json.contains("\"elements\""))check(!parsed.getElements().isEmpty(),"Native element deserializer retains authored geometry");
        }
        System.out.printf("PASS: %d checks; %d states; %d cached shapes; cold construction %.1f ms; 96,000 warm lookups %.2f ms, zero world reads. Not an FPS measurement.%n",checks,pole.getStateDefinition().getPossibleStates().size(),identities.size(),cold/1e6,warm/1e6);
    }
}
