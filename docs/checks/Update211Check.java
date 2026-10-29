import com.destan.trafficengine.block.*;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.block.entity.RailingPaintBlockEntity;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.packets.cts.PaintBrushPacket;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Native regression checks; does not start Minecraft or produce packaged artifacts. */
public final class Update211Check {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class World implements BlockGetter {
        public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Shape queried entity");}
        public BlockState getBlockState(BlockPos p){throw new AssertionError("Shape queried world");}
        public FluidState getFluidState(BlockPos p){throw new AssertionError("Shape queried fluid");}
        public int getHeight(){return 384;}
        public int getMinBuildHeight(){return -64;}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        var previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);
        List<RoadInformationSignBlock> signs=new ArrayList<>();
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        try {for(var kind:RoadInformationSignBlock.Kind.values())signs.add(new RoadInformationSignBlock(kind));}
        finally {holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        World world=new World();int states=0;int maxOutlineEdges=0;
        // Exercise the vanilla JSON parser: it rejects out-of-range placeholder bounds
        // before the custom face bakery can use the real mesh vertices.
        int modelCount=0;
        var modelRoot=java.nio.file.Path.of("src/main/resources/assets/trafficengine/models/block");
        try(var files=java.nio.file.Files.list(modelRoot)) {
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()) {
                String name=path.getFileName().toString();
                if(!(name.startsWith("road_direction_sign")||name.startsWith("highway_direction_sign")||name.startsWith("large_road_direction_sign")))continue;
                net.minecraft.client.renderer.block.model.BlockModel.fromString(java.nio.file.Files.readString(path));modelCount++;
            }
        }
        check(modelCount>=25,"All road sign JSON models loaded with vanilla parser");
        for(var color:PaintColor.values()) {
            check(RailingPaintBlockEntity.tint(true,color)==0xFFFFFFFF,"Gray metal is never multiplied by a stale dye");
            int tint=RailingPaintBlockEntity.tint(false,color);
            check(color==PaintColor.NONE ? tint==0xFF797979 : tint==color.getTextureARGB(),"Pending paint stays neutral; received dye is exact");
        }
        for(var color:PaintColor.values())if(color!=PaintColor.NONE)
            check(color.getTextureARGB()==color.getTextureColor().getAsARGB(),"Raw brush dye matches canonical swatch: "+color);

        for(var sign:signs)for(var state:sign.getStateDefinition().getPossibleStates()) {
            states++;var shape=state.getShape(world,BlockPos.ZERO);
            check(!shape.isEmpty()&&shape==state.getShape(world,BlockPos.ZERO),"Nonempty cached shape");
            int[] edges={0};shape.forAllEdges((a,b,c,d,e,f)->edges[0]++);
            maxOutlineEdges=Math.max(maxOutlineEdges,edges[0]);
            check(edges[0]<=640,"Bounded diagonal targeting outline: "+edges[0]);
            check(shape==state.cycle(WritableTrafficSign.WATERLOGGED).getShape(world,BlockPos.ZERO),"Water state aliases shape");
            var twice=sign.mirror(sign.mirror(state,net.minecraft.world.level.block.Mirror.LEFT_RIGHT),net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
            check(twice==state,"Mirroring twice restores every diagonal state");
            var rotated=state;
            for(int i=0;i<4;i++)rotated=sign.rotate(rotated,net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            check(rotated==state,"Four rotations restore every mount state");
            boolean mounted=state.getValue(RoadInformationSignBlock.POST_MOUNTED);
            boolean wall=sign.kind()==RoadInformationSignBlock.Kind.VARIABLE_SPEED&&state.getValue(RoadInformationSignBlock.WALL_MOUNTED);
            boolean hanging=!mounted&&!wall&&sign.kind()!=RoadInformationSignBlock.Kind.VARIABLE_SPEED&&state.getValue(RoadInformationSignBlock.HANGING);
            if(hanging)check(Math.abs(shape.bounds().maxY-1)<1e-8,"Hanging panel meets overhead mount");
            if(sign.kind()==RoadInformationSignBlock.Kind.LARGE&&!state.getValue(RoadInformationSignBlock.DIAGONAL)) {
                var b=shape.bounds();var facing=state.getValue(RoadInformationSignBlock.FACING);
                check(Math.abs((facing.getAxis()==Direction.Axis.X?b.getZsize():b.getXsize())-4)<1e-8,"Large panel remains four blocks wide");
            }
        }
        // Integral rear mounts must not produce little targeting edges on the face.
        var compact=signs.stream().filter(b->b.kind()==RoadInformationSignBlock.Kind.DIRECTION).findFirst().orElseThrow();
        var north=compact.defaultBlockState().setValue(WritableTrafficSign.FACING,Direction.NORTH);
        int[] hidden={0}; north.getShape(world,BlockPos.ZERO).forAllEdges((x,y,z,X,Y,Z)->{
            if (Math.min(z,Z)<7D/16 && Math.max(z,Z)>4.5D/16) hidden[0]++;
        });
        check(hidden[0]==0,"No hidden bracket edges extend from the plate towards the carrier");
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        CurvedTrafficPoleBlock curve;
        try { curve=new CurvedTrafficPoleBlock(); }
        finally {holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int part:new int[]{4,5})for(Direction side:Direction.Plane.HORIZONTAL) {
            var state=curve.defaultBlockState().setValue(CurvedTrafficPoleBlock.FACING,facing).setValue(CurvedTrafficPoleBlock.PART,part);
            check(CurvedTrafficPoleBlock.isMountingPost(state,side),"Curved arm accepts a road sign on every horizontal face");
            check(curve.canConnect(state,side),"Mounting and connection rules agree");
        }
        for(int pass=0;pass<100;pass++)for(var sign:signs)for(var state:sign.getStateDefinition().getPossibleStates())state.getShape(world,BlockPos.ZERO);
        var category=PaintBrushPacket.class.getDeclaredField("category");category.setAccessible(true);
        for(String value:new String[]{"ALL","LINES","ARROWS","LETTERS","NUMBERS","SYMBOLS","FAVORITES"}) {
            var source=new PaintBrushPacket(PacketStatus.OK).withCategory(value);
            var target=new PaintBrushPacket(PacketStatus.OK);target.deserializeNbt(source.serializeNbt());
            check(value.equals(category.get(target)),"Brush category persists: "+value);
        }
        var missing=new PaintBrushPacket(PacketStatus.OK);
        check(!missing.serializeNbt().getCompound("Data").contains("pattern_category"),"Options packets leave category absent");
        var received=new PaintBrushPacket(PacketStatus.OK).withCategory("LINES");received.deserializeNbt(missing.serializeNbt());
        check(category.get(received)==null,"Legacy packet cannot overwrite category");
        var invalid=missing.serializeNbt();invalid.getCompound("Data").putString("pattern_category","INVALID");received.deserializeNbt(invalid);
        check(category.get(received)==null,"Unknown category rejected");
        // Isolate the NBT codec from deferred platform registry initialization in this headless test.
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)uf.get(null);
        var entity=(RailingPaintBlockEntity)unsafe.allocateInstance(RailingPaintBlockEntity.class);
        var load=Arrays.stream(RailingPaintBlockEntity.class.getDeclaredMethods()).filter(m->m.getName().equals("loadAdditional")||m.getName().equals("load")).findFirst().orElseThrow();load.setAccessible(true);
        var save=Arrays.stream(RailingPaintBlockEntity.class.getDeclaredMethods()).filter(m->m.getName().equals("saveAdditional")).findFirst().orElseThrow();save.setAccessible(true);
        for(int index=-1;index<=15;index++) {
            var input=new CompoundTag();input.putInt("color",index);
            if(load.getParameterCount()==1)load.invoke(entity,input);else load.invoke(entity,input,null);
            check(entity.getColor()==PaintColor.getByIndex(index),"Railing dye loads "+index);
            var output=new CompoundTag();if(save.getParameterCount()==1)save.invoke(entity,output);else save.invoke(entity,output,null);
            check(output.contains("color") && output.getInt("color")==index,"Railing dye persists "+index);
        }
        for(int value:new int[]{-100,16,999}) {
            var input=new CompoundTag();input.putInt("color",value);
            if(load.getParameterCount()==1)load.invoke(entity,input);else load.invoke(entity,input,null);
            check(entity.getColor()==PaintColor.NONE,"Invalid dye falls back to unpainted");
        }
        var legacy=new CompoundTag();if(load.getParameterCount()==1)load.invoke(entity,legacy);else load.invoke(entity,legacy,null);
        check(entity.getColor()==PaintColor.NONE,"Legacy railing has no dye override");
        System.out.println("Models: "+modelCount+"; maximum outline edges: "+maxOutlineEdges);
        System.out.println("PASS "+checks+" assertions; "+states+" sign states; "+(100*states)+" warm cached shape lookups without world reads; brush packet categories; all railing dye NBT values. No in-game/FPS claim.");
    }
}
