import com.destan.trafficengine.block.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Native cached-shape and terrain/connection regression checks; no renderer or game world. */
public final class RailingGateSlopeCheck {
    static int count;
    static void check(boolean ok,String reason){count++;if(!ok)throw new AssertionError(reason);}
    static final class World implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();int reads;
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public BlockState getBlockState(BlockPos p){reads++;return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public int getHeight(){return 384;}
        public int getMinBuildHeight(){return -64;}
    }
    public static void main(String[]args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);
        PedestrianRailingGateBlock gate;PedestrianRailingBlock rail;SlopedPedestrianRailingBlock slope;SidewalkSlopeBlock layerBlock;
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        try{gate=new PedestrianRailingGateBlock();rail=new PedestrianRailingBlock();slope=new SlopedPedestrianRailingBlock();layerBlock=new SidewalkSlopeBlock();}
        finally{holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        World world=new World();BlockPos pos=new BlockPos(0,2,0);var context=CollisionContext.empty();
        check(gate.defaultBlockState().getValue(PedestrianRailingGateBlock.GRAY),"Gate defaults to matching gray");
        check(!gate.defaultBlockState().getValue(PedestrianRailingGateBlock.OPEN),"Gate defaults closed");
        check(rail.connectsTo(gate.defaultBlockState(),false,Direction.EAST),"Flat railing connects to gate side");
        check(!rail.connectsTo(gate.defaultBlockState(),false,Direction.NORTH),"Gate does not connect from its walking direction");
        Set<VoxelShape> gates=Collections.newSetFromMap(new IdentityHashMap<>()),ramps=Collections.newSetFromMap(new IdentityHashMap<>());
        for(BlockState s:gate.getStateDefinition().getPossibleStates()){
            var outline=s.getShape(world,pos);gates.add(outline);
            check(outline==s.getShape(world,pos),"Gate outline identity cached");
            check(s.getCollisionShape(world,pos).isEmpty()==s.getValue(PedestrianRailingGateBlock.OPEN),"Open gate permits passage; closed gate blocks");
            check(!s.hasBlockEntity(),"Gate has no block entity");
        }
        check(gates.size()==16,"Gate has exactly sixteen shared outlines for stacked gates");
        long start=System.nanoTime();
        for(BlockState s:slope.getStateDefinition().getPossibleStates()){
            var shape=s.getShape(world,pos);ramps.add(shape);
            check(shape==s.getShape(world,pos),"Ramp shape identity cached");
            check(!s.getCollisionShape(world,pos).isEmpty(),"Every ramp grade retains a barrier");
            check(!s.hasBlockEntity(),"Ramp has no block entity");
            check(shape.bounds().minY>=-1-1e-7&&shape.bounds().maxY<=2+1e-7,"Anchored ramp bounds stay within authored range");
            var colored=s.cycle(SlopedPedestrianRailingBlock.GRAY).cycle(SlopedPedestrianRailingBlock.WATERLOGGED);
            check(shape==colored.getShape(world,pos),"Color/water do not allocate another ramp shape");
        }
        long cold=System.nanoTime()-start;
        check(ramps.size()==864,"Ascending/descending and level profiles have bounded shared outlines");
        world.reads=0;start=System.nanoTime();
        for(int n=0;n<5;n++)for(BlockState s:slope.getStateDefinition().getPossibleStates())s.getShape(world,pos);
        long warm=System.nanoTime()-start;
        check(world.reads==0,"Warm shape lookup does not inspect world terrain");
        var grade=SlopedPedestrianRailingBlock.class.getDeclaredMethod("grade",BlockGetter.class,BlockPos.class,Direction.class,int.class);grade.setAccessible(true);
        for(int layer=1;layer<=8;layer++){
            world.blocks.clear();world.blocks.put(pos.below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,layer));
            int sink=8-layer;
            check(RailingGeometry.sinkFor(world.getBlockState(pos.below()))==sink,"All eight road layers anchor correctly");
            var grounded=SlopedPedestrianRailingBlock.connections(slope.defaultBlockState(),world,pos);
            check(grounded.getValue(SlopedPedestrianRailingBlock.SINK)==sink,"Ramp reads support layer on update");
            var back=rail.defaultBlockState().setValue(PedestrianRailingBlock.SINK,sink);
            world.blocks.put(pos.south(),back);
            check(SlopedPedestrianRailingBlock.connections(grounded,world,pos).getValue(SlopedPedestrianRailingBlock.BACK),"Matching flat lower rail attaches");
            check(RailingGeometry.sinkFor(back)==sink,"Stacked flat railing inherits its terrain offset");
            world.blocks.put(pos.north().below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,Math.min(8,layer+1)));
            if(layer<8)check((int)grade.invoke(null,world,pos,Direction.NORTH,sink)==1,"Adjacent road layers automatically select the 1/8 grade");
        }
        for(Direction dir:Direction.Plane.HORIZONTAL)for(int from=1;from<=8;from++)for(int to=1;to<=8;to++){
            world.blocks.clear();
            world.blocks.put(pos.below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,from));
            world.blocks.put(pos.relative(dir).below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,to));
            var initial=slope.defaultBlockState().setValue(SlopedPedestrianRailingBlock.FACING,dir);
            var actual=SlopedPedestrianRailingBlock.terrainProfile(initial,world,pos);
            check(actual.getValue(SlopedPedestrianRailingBlock.RISE)==Math.abs(to-from),"All ascending/descending layer pairs select correct grade");
            check(actual.getValue(SlopedPedestrianRailingBlock.DESCENDING)==(to<from),"Slope direction follows terrain");
            check(actual.getValue(SlopedPedestrianRailingBlock.SINK)==8-from,"Anchor sits on lower surface");
            check(SlopedPedestrianRailingBlock.terrainProfile(actual,world,pos)==actual,"Terrain profile is stable without further updates");
            // Changing the distant support layer is reflected by the next one-shot terrain event.
            world.blocks.put(pos.relative(dir).below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,from));
            check(SlopedPedestrianRailingBlock.terrainProfile(actual,world,pos).getValue(SlopedPedestrianRailingBlock.RISE)==0,"Layer edit changes incline to level");
        }
        for(Direction dir:Direction.Plane.HORIZONTAL)for(int from=1;from<=8;from++){
            world.blocks.clear();world.blocks.put(pos.below(),layerBlock.defaultBlockState().setValue(SidewalkSlopeBlock.LAYERS,from));
            world.blocks.put(pos.relative(dir).below(2),Blocks.STONE.defaultBlockState());
            var actual=SlopedPedestrianRailingBlock.terrainProfile(slope.defaultBlockState().setValue(SlopedPedestrianRailingBlock.FACING,dir),world,pos);
            check(actual.getValue(SlopedPedestrianRailingBlock.RISE)==from&&actual.getValue(SlopedPedestrianRailingBlock.DESCENDING),"Slab edge descends to lower full-block ground");
        }
        world.blocks.clear();world.blocks.put(pos.north(),Blocks.STONE.defaultBlockState());
        check((int)grade.invoke(null,world,pos,Direction.NORTH,0)==8,"Full-block stair selects the 8/8 grade");
        for(Direction d:Direction.Plane.HORIZONTAL){
            var state=slope.defaultBlockState().setValue(SlopedPedestrianRailingBlock.FACING,d);
            check(rail.connectsTo(state,false,d.getOpposite()),"Flat lower end attaches for every ramp direction");
            check(!rail.connectsTo(state,false,d),"Upper flat arm does not overlap the sloped span");
            var rotated=slope.rotate(state,Rotation.CLOCKWISE_90);
            check(rotated.getValue(SlopedPedestrianRailingBlock.FACING)==d.getClockWise(),"Structure rotation preserves ascent direction");
        }
        System.out.printf("PASS: %,d checks; gate states=%d; ramp states=%d; cached outlines=%d/%d; cold shape sweep=%.1f ms; 23,040 warm lookups=%.1f ms, zero world reads. Not an FPS benchmark.%n",count,gate.getStateDefinition().getPossibleStates().size(),slope.getStateDefinition().getPossibleStates().size(),gates.size(),ramps.size(),cold/1e6,warm/1e6);
    }
}
