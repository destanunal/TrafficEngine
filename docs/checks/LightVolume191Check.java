import com.destan.trafficengine.client.lighting.*;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
public final class LightVolume191Check {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        AtomicInteger reads=new AtomicInteger();
        BlockAndTintGetter air=(BlockAndTintGetter)Proxy.newProxyInstance(LightVolume191Check.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},(p,m,a)->{
            if(m.getName().equals("getBlockState")){reads.incrementAndGet();return Blocks.AIR.defaultBlockState();}
            if(m.getName().equals("getHeight"))return 384;
            if(m.getName().equals("getMinBuildHeight"))return -64;
            throw new UnsupportedOperationException(m.getName());
        });
        var volume=ClientColoredLighting.propagate(air,BlockPos.ZERO,RgbLightMath.pack(15,0,0));
        check(volume.size()==4089&&reads.get()==4088,"One maximum-strength source visits the finite 14-block Manhattan radius exactly once");
        check(volume.get(BlockPos.asLong(14,0,0))==RgbLightMath.pack(1,0,0)&&!volume.containsKey(BlockPos.asLong(15,0,0)),"Light decays to zero at its boundary");
        check(ClientColoredLighting.propagate(air,BlockPos.ZERO,0).isEmpty(),"Disabled source does no traversal");
        int directionalCells=0;
        for(var front:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            reads.set(0);
            var beam=ClientColoredLighting.propagate(air,BlockPos.ZERO,RgbLightMath.pack(15,0,0),front);
            check(beam.get(BlockPos.ZERO.relative(front,14).asLong())==RgbLightMath.pack(1,0,0),"LED forward intensity is preserved");
            check(!beam.containsKey(BlockPos.ZERO.relative(front.getOpposite()).asLong()),"LED cannot light the cell behind its panel");
            for(long key:beam.keySet()) check(BlockPos.getX(key)*front.getStepX()+BlockPos.getZ(key)*front.getStepZ()>=0,"Flood-fill never wraps into the rear half-space");
            check(reads.get()==beam.size()-1 && beam.size()<volume.size(),"Directional light reads each accepted cell once and visits fewer cells");
            directionalCells+=beam.size();
        }
        BlockAndTintGetter wall=(BlockAndTintGetter)Proxy.newProxyInstance(LightVolume191Check.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},(p,m,a)->{
            if(m.getName().equals("getBlockState"))return ((BlockPos)a[0]).getZ()==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
            if(m.getName().equals("getHeight"))return 384;
            if(m.getName().equals("getMinBuildHeight"))return -64;
            throw new UnsupportedOperationException(m.getName());
        });
        var blocked=ClientColoredLighting.propagate(wall,BlockPos.ZERO,RgbLightMath.pack(5,0,0),net.minecraft.core.Direction.NORTH);
        check(!blocked.containsKey(BlockPos.asLong(0,0,-2)),"An opaque wall blocks the LED beam");
        System.out.println("LED: four facing directions, "+directionalCells+" total cached cells; no rear leakage, preserved forward brightness and opaque-wall blocking.");
        ColoredLightIndex index=new ColoredLightIndex();Object red=new Object(),blue=new Object();
        index.replace(red,volume);index.publish();
        var snapshot=ColoredLightIndex.class.getDeclaredField("snapshot");snapshot.setAccessible(true);Object old=snapshot.get(index);
        var volumesField=ColoredLightIndex.class.getDeclaredField("volumes");volumesField.setAccessible(true);
        var stored=(java.util.Map<?,?>)volumesField.get(index);
        var sourceSections=(it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<?>)stored.get(red);
        var published=(it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<?>)snapshot.get(index);
        long sharedBytes=0;
        var keyArray=it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap.class.getDeclaredField("key");keyArray.setAccessible(true);
        for(long section:sourceSections.keySet()) {
            check(sourceSections.get(section)==published.get(section),"Single-source sections share immutable storage");
            sharedBytes+=((short[])keyArray.get(sourceSections.get(section))).length*4L;
        }
        check(!index.publish(),"Unchanged sources do not republish");
        System.out.println("Single-source RGB storage: "+sharedBytes+" bytes of duplicate backing arrays avoided in this maximum-radius fixture (object headers excluded).");
        Long2IntOpenHashMap other=new Long2IntOpenHashMap();other.put(BlockPos.ZERO.asLong(),RgbLightMath.pack(0,0,5));
        index.replace(blue,other);index.publish();
        check(index.get(0,0,0)==RgbLightMath.pack(15,0,5),"Overlapping sources merge independent color channels");
        check(old!=snapshot.get(index),"Chunk workers receive a fresh immutable snapshot");
        check(((it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap)published.get(net.minecraft.core.SectionPos.asLong(0,0,0))).get((short)0)==RgbLightMath.pack(15,0,0),"Later merges do not mutate a shared old snapshot");
        index.remove(red);index.publish();check(index.get(0,0,0)==RgbLightMath.pack(0,0,5),"Removal preserves another source's light");
        index.remove(blue);index.publish();check(index.get(0,0,0)==0,"Removed sources do not retain stale light");
        int[] dynamic={0x00F000F0,0x00F00080,0x00900000,0};
        int[] promoted=ClientColoredLighting.promoteLight(dynamic,10);
        check(dynamic[3]==0&&promoted[0]==dynamic[0]&&(promoted[2]&0xFFF00000)==(dynamic[2]&0xFFF00000),"Dynamic brightness and sky light are preserved without mutating shared input");
        System.out.println("PASS: maximum propagation 4,089 cells / 4,088 world reads; immutable publication, color merging/removal and dynamic-light preservation. These bounds are not an FPS measurement.");
    }
}
