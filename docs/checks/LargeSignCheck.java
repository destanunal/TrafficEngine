import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.data.RoadSignDesign;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.packets.cts.RoadInformationSignPacket;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Native data/model checks, run against each Minecraft version's compiled classes. */
public final class LargeSignCheck {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Object field(Object value,String name)throws Exception {
        var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    static final class World implements BlockGetter {
        public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Cached shape queried entity");}
        public BlockState getBlockState(BlockPos p){throw new AssertionError("Cached shape queried world");}
        public FluidState getFluidState(BlockPos p){throw new AssertionError("Cached shape queried fluid");}
        public int getHeight(){return 384;}
        public int getMinBuildHeight(){return -64;}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(int rows=1;rows<=4;rows++) {
            for(int size:new int[]{70,100,160,180,195,200,205,250,300,395,400,405}) {
                RoadSignDesign d=new RoadSignDesign(rows,0,size,0,2,false,false,false);
                check(d.textSize()==Math.min(400,size),"Serialized design supports up to 400%");
                for(var kind:RoadInformationSignBlock.Kind.values()) {
                    int capacity=RoadInformationSignBlock.rowCapacity(kind);
                    int max=RoadInformationSignBlock.maxTextSize(kind);
                    var limited=d.constrainedTo(capacity,max);
                    check(limited.textSize()==Math.min(kind==RoadInformationSignBlock.Kind.LARGE?400:200,size),"Per-sign text size limit");
                    check(limited.rows()==Math.min(rows,capacity),"Per-sign row limit");
                    check(limited.constrainedTo(capacity,max)==limited,"Valid settings are reused without allocation");
                    check(RoadSignDesign.load(limited.save()).equals(limited),"Per-sign size persists");
                }
                check(RoadSignDesign.load(d.save()).equals(d),"Row count and font size survive NBT");
                for(int color=0;color<=4;color++) {
                    var fourth=d.withPanelColor(3,color);
                    check(RoadSignDesign.load(fourth.save()).panelColor(3)==color,"Fourth panel color survives NBT");
                    check(fourth.panelColor(0)==0,"Fourth panel color cannot alter first row");
                }
            }
        }
        check(RoadSignDesign.LARGE_DEFAULT.rows()==4&&RoadSignDesign.LARGE_DEFAULT.textSize()==200,"Large signs default to four rows at 200%");
        CompoundTag old=new CompoundTag();old.putInt("rows",3);old.putInt("text_size",160);old.putInt("white_panels",5);
        var legacy=RoadSignDesign.load(old);
        check(legacy.rows()==3&&legacy.textSize()==160,"Legacy signs retain saved layout");
        check(legacy.panelColor(0)==1&&legacy.panelColor(1)==0&&legacy.panelColor(2)==1&&legacy.panelColor(3)==0,"Legacy panel mask migration");
        var design=new RoadSignDesign(4,1,400,0,2,true,false,true).withPanelColor(3,3);
        var packet=new RoadInformationSignPacket(new BlockPos(15,64,-16),new String[]{"İstanbul","München","Edirne","Bursa"},new int[]{1,2,3,4},new String[]{"10","20","30","40"},"D100",1,70,true,design);
        var saved=packet.serializeNbt();
        var received=new RoadInformationSignPacket(PacketStatus.OK);received.deserializeNbt(saved);
        check(Arrays.equals((String[])field(received,"lines"),new String[]{"İstanbul","München","Edirne","Bursa"}),"Four translated destinations round-trip");
        check(Arrays.equals((String[])field(received,"distances"),new String[]{"10","20","30","40"}),"Fourth distance round-trip");
        check(Arrays.equals((int[])field(received,"arrows"),new int[]{1,2,3,4}),"Fourth arrow round-trip");
        check(field(received,"design").equals(design),"Packet preserves 400% and fourth panel color");
        saved.getCompound("Data").remove("line3");saved.getCompound("Data").remove("distance3");received.deserializeNbt(saved);
        check(((String[])field(received,"lines"))[3].isEmpty()&&((String[])field(received,"distances"))[3].isEmpty(),"Old packets load blank fourth fields");
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        var previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);
        List<RoadInformationSignBlock> signs=new ArrayList<>();
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        try {for(var kind:RoadInformationSignBlock.Kind.values())signs.add(new RoadInformationSignBlock(kind));}
        finally {holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        World world=new World();int states=0;
        for(var sign:signs)for(var state:sign.getStateDefinition().getPossibleStates()) {
            states++;
            var shape=state.getShape(world,BlockPos.ZERO);
            check(!shape.isEmpty()&&shape==state.getShape(world,BlockPos.ZERO),"Shape is nonempty and cached");
            check(shape==state.cycle(com.destan.trafficengine.block.WritableTrafficSign.WATERLOGGED).getShape(world,BlockPos.ZERO),"Water state reuses cached geometry");
            if(sign.kind()==RoadInformationSignBlock.Kind.LARGE) {
                var b=shape.bounds();var facing=state.getValue(RoadInformationSignBlock.FACING);
                check(Math.abs((facing.getAxis()==Direction.Axis.X?b.getZsize():b.getXsize())-4)<1e-8,"Large panel is four blocks wide in every rotation");
                check(Math.abs(b.getYsize()-3)<1e-8,"Large panel is three blocks high for all mount modes");
            }
        }
        for(int pass=0;pass<100;pass++)for(var sign:signs)for(var state:sign.getStateDefinition().getPossibleStates())state.getShape(world,BlockPos.ZERO);
        for(String path:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))) {
            var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(java.nio.file.Files.readString(java.nio.file.Path.of(path)));
            check(!model.getElements().isEmpty(),"Native Minecraft JSON parser accepts "+path);
        }
        System.out.println("PASS "+checks+" assertions; "+states+" states; 25,600 warm shape lookups with no world reads; four-row data, large 400% and regular 200% caps.");
    }
}
