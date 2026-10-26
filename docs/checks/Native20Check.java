import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.data.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import java.nio.ByteBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Native buffer regression, including the bounded texture cache's allocation-free fallback. */
public final class Native20Check {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void slot(TrafficLightBlockEntity signal,int slot,TrafficLightColor color,float expectedX,float expectedWidth)throws Exception{
        var buffers=new Bulb191Check.Buffers();
        TrafficLightTextureManager.renderSlot(new RenderContext<>(signal,new PoseStack(),buffers,0,0,0),signal,slot,TrafficLightIcon.NONE,color,0);
        check(buffers.buffers.size()==2,"One background and one stencil batch");
        for(int i=0;i<2;i++){
            var b=buffers.buffers.get(i);Object mesh;
            try{mesh=BufferBuilder.class.getMethod("end").invoke(b);}catch(NoSuchMethodException ex){mesh=BufferBuilder.class.getMethod("buildOrThrow").invoke(b);}
            Object state=mesh.getClass().getMethod("drawState").invoke(mesh);int count=(int)state.getClass().getMethod("vertexCount").invoke(state);
            check(count==(i==0?16:4),"Complete background/stencil vertices");
            if(i==1){
                ByteBuffer bytes=(ByteBuffer)mesh.getClass().getMethod("vertexBuffer").invoke(mesh);int stride=buffers.types.get(i).format().getVertexSize();
                float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(int n=0;n<count;n++){float x=bytes.getFloat(n*stride);min=Math.min(min,x);max=Math.max(max,x);int r=bytes.get(n*stride+12)&255,g=bytes.get(n*stride+13)&255;check(color==TrafficLightColor.RED?r>g:g>r,"Stencil retains the selected lamp's color");}
                check(Math.abs(min-expectedX)<1e-6&&Math.abs(max-min-expectedWidth)<1e-6,"Each bulb renders its own mask coordinates");
            }
            try{mesh.getClass().getMethod("close").invoke(mesh);}catch(NoSuchMethodException ex){mesh.getClass().getMethod("release").invoke(mesh);}
        }
        for(Object nativeBuffer:buffers.nativeBuffers)nativeBuffer.getClass().getMethod("close").invoke(nativeBuffer);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Update20RegressionCheck.headlessIcons();
        Bulb191Check.main(args);LightVolume191Check.main(args);
        var ctor=TrafficLightBlockEntity.class.getDeclaredConstructor(BlockEntityType.class,BlockPos.class,BlockState.class);ctor.setAccessible(true);
        var signal=ctor.newInstance(BlockEntityType.CHEST,BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
        signal.setSignalSymbols(SignalSymbols.DEFAULT.with(0,TrafficLightIcon.NONE,PixelPattern.of(new long[]{3,0,0,0})).with(1,TrafficLightIcon.NONE,PixelPattern.of(new long[]{256,0,0,0})));
        var field=TrafficLightTextureManager.class.getDeclaredField("STENCILS");field.setAccessible(true);Map<Object,Object> cache=(Map<Object,Object>)field.get(null);
        try{
            // The same production fallback is used after 256 unique textures; no GL context is needed here.
            for(int i=0;i<256;i++)cache.put(PixelPattern.of(new long[]{i+10000,1,0,0}),null);
            slot(signal,0,TrafficLightColor.RED,0,.5f);slot(signal,1,TrafficLightColor.GREEN,2,.25f);
            check(cache.size()==256,"Overflow never grows the texture cache");
        }finally{cache.clear();}
        System.out.println("PASS: per-bulb custom masks use independent native vertices/colors and respect the 256-texture fallback bound.");
    }
}
