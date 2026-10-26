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
        CurvedTrafficPoleBlock pole;
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        long start=System.nanoTime();
        try{pole=new CurvedTrafficPoleBlock();}
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
            for(Direction side:Direction.values())check(pole.canConnect(state,side)==(port&&side==Direction.DOWN),"Only the real lower tube ports connect to posts");
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
        check(identities.size()==24,"Bounded table contains exactly six cells by four directions");
        world.reads=0;start=System.nanoTime();
        for(int pass=0;pass<1000;pass++)for(BlockState state:pole.getStateDefinition().getPossibleStates())state.getShape(world,origin);
        long warm=System.nanoTime()-start;
        check(world.reads==0,"48,000 warm lookups read no world state");
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
        System.out.printf("PASS: %d checks; %d states; %d cached shapes; cold construction %.1f ms; 48,000 warm lookups %.2f ms, zero world reads. Not an FPS measurement.%n",checks,pole.getStateDefinition().getPossibleStates().size(),identities.size(),cold/1e6,warm/1e6);
    }
}
