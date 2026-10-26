import com.destan.trafficengine.block.ChevronPanelBlock;
import com.destan.trafficengine.block.HydraulicBollardBlock;
import com.destan.trafficengine.block.PedestrianRailingBlock;
import com.destan.trafficengine.client.lighting.RgbLightMath;
import com.destan.trafficengine.data.PixelBrush;
import com.destan.trafficengine.util.SignPostMigration;
import com.destan.trafficengine.util.SignTextureLimits;
import com.destan.trafficengine.util.Utils;
import java.util.*;
import java.util.zip.DataFormatException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Standalone checks against compiled production classes. Never modifies a game/world. */
public final class Update191RegressionCheck {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static void brush() {
        PixelBrush b = new PixelBrush(8);
        b.setSize(100); check(b.size()==8,"Brush upper limit");
        Set<Integer> pixels = new HashSet<>(); b.stamp(0,0,16,pixels::add);
        check(pixels.size()==25 && pixels.contains(0) && pixels.contains(68),"Large brush clips at the canvas edge");
        b.setSize(-3); check(b.size()==1,"Brush lower limit");
        pixels.clear(); b.stroke(0,0,15,15,16,pixels::add);
        check(pixels.size()==16,"Fast pointer diagonal has no gaps");
        for(int n=0;n<16;n++) check(pixels.contains(n*17),"Continuous diagonal");
        b.setSize(3); pixels.clear(); b.stamp(15,15,16,pixels::add);
        check(pixels.size()==4 && pixels.stream().allMatch(i->i>=0&&i<256),"Bottom/right clipping");
        long[] stencil=new long[4];
        b.stroke(2,2,12,2,16,i->stencil[i>>>6]|=1L<<(i&63));
        check(Long.bitCount(stencil[0])==39,"Three-pixel brush sweeps a contiguous rectangle");
        b.stroke(2,2,12,2,16,i->stencil[i>>>6]&=~(1L<<(i&63)));
        check(Arrays.stream(stencil).allMatch(x->x==0),"Eraser covers exactly the drawn stroke");
    }
    static void lighting() {
        for(int r=0;r<16;r++) for(int g=0;g<16;g++) for(int b=0;b<16;b++) {
            int p=RgbLightMath.pack(r,g,b);
            check(RgbLightMath.red(p)==r&&RgbLightMath.green(p)==g&&RgbLightMath.blue(p)==b,"RGB packing");
            check(RgbLightMath.maximum(RgbLightMath.decay(p))==Math.max(0,Math.max(r,Math.max(g,b))-1),"Finite light propagation");
        }
        check(RgbLightMath.tintAbgr(0xFFFFFFFF,1,0,0)==0xFF0000FF,"Embeddium red remains red in ABGR");
        check(RgbLightMath.tintArgb(0xFFFFFFFF,1,0,0)==0xFFFF0000,"Sodium red remains red in ARGB");
        check(RgbLightMath.tintAbgr(0xFFFFFFFF,0,0,1)==0xFFFF0000,"ABGR blue");
        check(RgbLightMath.tintArgb(0xFFFFFFFF,0,0,1)==0xFF0000FF,"ARGB blue");
        check((RgbLightMath.tintArgb(0x80402010,.5F,.5F,.5F)>>>24)==0x80,"Alpha preserved");
    }
    static void data() throws Exception {
        byte[] bytes=new byte[8192]; new Random(42).nextBytes(bytes);
        check(Arrays.equals(bytes,Utils.decompress(Utils.compress(bytes,6))),"Compressed data round trip");
        check(Utils.decompress(Utils.compress(new byte[0],6)).length==0,"Empty stream round trip");
        byte[] compressed=Utils.compress(bytes,6);
        try { Utils.decompress(Arrays.copyOf(compressed,compressed.length-3)); throw new AssertionError("Truncated input accepted"); }
        catch(DataFormatException expected) {}
        try { Utils.decompress(Utils.compress(new byte[1024*1024+1],6)); throw new AssertionError("Expansion limit bypassed"); }
        catch(DataFormatException expected) {}
        byte[] png=new byte[33];
        java.nio.ByteBuffer.wrap(png).putInt(0x89504E47).putInt(0x0D0A1A0A).putInt(13).putInt(0x49484452).putInt(32).putInt(32);
        check(SignTextureLimits.validPng(png,32,32),"Valid bounded PNG header");
        check(!SignTextureLimits.validPng(png,31,32),"Metadata must match image dimensions");
        check(!SignTextureLimits.validPng(Arrays.copyOf(png,65537),32,32),"Encoded-size limit");
        check(!SignTextureLimits.validPng(Arrays.copyOf(png,24),32,32),"Incomplete header rejected");
        java.nio.ByteBuffer.wrap(png).putInt(16,Integer.MAX_VALUE);
        check(!SignTextureLimits.validPng(png,32,32),"Oversized native image rejected before decode");
    }
    static void blocks() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"); holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        Object previous=holders.get(BuiltInRegistries.BLOCK); boolean wasFrozen=frozen.getBoolean(BuiltInRegistries.BLOCK);
        ChevronPanelBlock sign; HydraulicBollardBlock bollard; PedestrianRailingBlock railing;
        frozen.setBoolean(BuiltInRegistries.BLOCK,false); holders.set(BuiltInRegistries.BLOCK,new IdentityHashMap<>());
        try {
            sign=new ChevronPanelBlock(); bollard=new HydraulicBollardBlock(); railing=new PedestrianRailingBlock();
            Registry.register(BuiltInRegistries.BLOCK,"trafficengine:regression_fixture",sign);
        } finally { holders.set(BuiltInRegistries.BLOCK,previous); frozen.setBoolean(BuiltInRegistries.BLOCK,wasFrozen); }
        check(!((Object)sign instanceof EntityBlock)&&!((Object)bollard instanceof EntityBlock)&&!((Object)railing instanceof EntityBlock),"New static equipment has no block entity ticker");
        var shapes=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var s:sign.getStateDefinition().getPossibleStates()) {
            var shape=sign.getShape(s,null,BlockPos.ZERO,CollisionContext.empty()); shapes.add(shape);
            check(shape==sign.getShape(s,null,BlockPos.ZERO,CollisionContext.empty()),"Sign cached shape identity");
            for(Mirror mirror:Mirror.values()) check(sign.mirror(sign.mirror(s,mirror),mirror).equals(s),"Mirror involution for cardinal/diagonal signs");
            var rotated=s;
            for(int i=0;i<4;i++) rotated=sign.rotate(rotated,Rotation.CLOCKWISE_90);
            check(rotated.equals(s),"Four quarter turns restore the sign");
            if(s.getValue(ChevronPanelBlock.DIAGONAL)) {
                check(!ChevronPanelBlock.connects(s,s.getValue(ChevronPanelBlock.FACING)),"No false joins across diagonal boards");
                var resized=ChevronPanelBlock.withSingle(s,!s.getValue(ChevronPanelBlock.SINGLE),null,BlockPos.ZERO);
                check(!resized.getValue(ChevronPanelBlock.LEFT)&&!resized.getValue(ChevronPanelBlock.RIGHT),"Diagonal resize clears joins without a world lookup");
            }
        }
        check(shapes.size()==16,"Sixteen precomputed sign shapes across all block states");
        for(int i=0;i<=8;i++) {
            var state=bollard.defaultBlockState().setValue(HydraulicBollardBlock.EXTENSION,i);
            var shape=bollard.getCollisionShape(state,null,BlockPos.ZERO,CollisionContext.empty());
            check(shape.isEmpty()==(i==0),"Retracted bollard has no obstruction");
            check(shape==bollard.getCollisionShape(state.cycle(HydraulicBollardBlock.RAISED),null,BlockPos.ZERO,CollisionContext.empty()),"Motion endpoint settings reuse cached geometry");
            if(i>0) check(Math.abs(shape.max(Direction.Axis.Y)-(0.5+i*1.75)/16)<1e-9,"Model and collision height agree");
        }
        for(var s:railing.getStateDefinition().getPossibleStates()) {
            var a=s.getShape(null,BlockPos.ZERO,CollisionContext.empty());
            check(a==s.cycle(PedestrianRailingBlock.GRAY).cycle(BlockStateProperties.WATERLOGGED).getShape(null,BlockPos.ZERO,CollisionContext.empty()),"Railing color/water share vanilla cached connections");
        }
        CompoundTag tag=new CompoundTag(); tag.putString("Name","trafficcraft:regression_fixture"); SignPostMigration.blockState(tag);
        check(tag.getString("Name").equals("trafficengine:regression_fixture"),"Existing legacy block palettes migrate before decoding");
        tag.putString("Name","trafficcraft:does_not_exist"); SignPostMigration.blockState(tag);
        check(tag.getString("Name").equals("trafficcraft:does_not_exist"),"Unknown legacy blocks are not silently replaced");
        tag.putString("Name","trafficengine:cantilever_arm"); SignPostMigration.blockState(tag);
        check(tag.getString("Name").equals(SignPostMigration.REPLACEMENT),"Prior post migration is preserved");
        var beRegistry=BuiltInRegistries.BLOCK_ENTITY_TYPE;
        previous=holders.get(beRegistry);wasFrozen=frozen.getBoolean(beRegistry);
        frozen.setBoolean(beRegistry,false);holders.set(beRegistry,new IdentityHashMap<>());
        try {
            var type=net.minecraft.world.level.block.entity.BlockEntityType.Builder.of((p,s)->(net.minecraft.world.level.block.entity.BlockEntity)null,Blocks.STONE).build(null);
            beRegistry.getClass().getMethod("createIntrusiveHolder",Object.class).invoke(beRegistry,type);
            Registry.register(beRegistry,"trafficengine:regression_fixture",type);
        } finally { holders.set(beRegistry,previous);frozen.setBoolean(beRegistry,wasFrozen); }
        java.lang.reflect.Method migrate=Arrays.stream(com.destan.trafficengine.mixin.LegacyWorldMigrationMixin.class.getDeclaredMethods())
            .filter(m->m.getName().equals("migrateLegacyBlockEntities")).findFirst().orElseThrow();migrate.setAccessible(true);
        for(String id:List.of("trafficengine:regression_fixture","trafficcraft:regression_fixture","minecraft:chest")) for(boolean air:List.of(false,true)) {
            CompoundTag entity=new CompoundTag();entity.putString("id",id);
            var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.block.entity.BlockEntity>("loadStatic",true);
            var state=(air?Blocks.AIR:Blocks.STONE).defaultBlockState();
            if(migrate.getParameterCount()==4)migrate.invoke(null,BlockPos.ZERO,state,entity,callback);
            else migrate.invoke(null,BlockPos.ZERO,state,entity,null,callback);
            check(callback.isCancelled()==(air&&!id.startsWith("minecraft:")),"Only mismatched Traffic Engine block entities skip construction");
            if(id.startsWith("trafficcraft:"))check(entity.getString("id").equals("trafficengine:regression_fixture"),"Legacy entity ID is migrated");
        }
    }
    public static void main(String[] args) throws Exception {
        brush(); lighting(); data(); blocks();
        System.out.println("PASS: clipped/resizable continuous brushes, ARGB/ABGR colors, bounded decoding, cached diagonal signs/bollards/railings, legacy palettes. No game world or release artifacts created.");
    }
}
