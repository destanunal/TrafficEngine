import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.client.ber.RenderContext;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import java.util.*;
public final class Bulb191Check {
    static final class Buffers implements MultiBufferSource {
        List<BufferBuilder> buffers=new ArrayList<>(); List<RenderType> types=new ArrayList<>(); List<Object> nativeBuffers=new ArrayList<>();
        public VertexConsumer getBuffer(RenderType type) {
            try {
                BufferBuilder b;
                try {
                    b=BufferBuilder.class.getConstructor(int.class).newInstance(4096);
                    BufferBuilder.class.getMethod("begin",VertexFormat.Mode.class,VertexFormat.class).invoke(b,VertexFormat.Mode.QUADS,type.format());
                } catch(NoSuchMethodException modern) {
                    Class<?> allocator=Class.forName("com.mojang.blaze3d.vertex.ByteBufferBuilder");
                    Object nativeBuffer=allocator.getConstructor(int.class).newInstance(4096);nativeBuffers.add(nativeBuffer);
                    b=BufferBuilder.class.getConstructor(allocator,VertexFormat.Mode.class,VertexFormat.class).newInstance(nativeBuffer,VertexFormat.Mode.QUADS,type.format());
                }
                buffers.add(b);types.add(type);return b;
            } catch(Exception e){throw new RuntimeException(e);}
        }
        void verify(boolean on) throws Exception {
            if(buffers.size()!=1)throw new AssertionError("One cached bulb batch");
            Object result;
            try{result=BufferBuilder.class.getMethod("end").invoke(buffers.get(0));}
            catch(NoSuchMethodException modern){result=BufferBuilder.class.getMethod("buildOrThrow").invoke(buffers.get(0));}
            Object state=result.getClass().getMethod("drawState").invoke(result);
            int count=(int)state.getClass().getMethod("vertexCount").invoke(state);
            if(count!=16)throw new AssertionError("Expected 16 complete bulb vertices, got "+count);
            VertexFormat expected=DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
            if(!types.get(0).format().equals(expected))throw new AssertionError("Wrong vertex layout");
            java.nio.ByteBuffer vertices=(java.nio.ByteBuffer)result.getClass().getMethod("vertexBuffer").invoke(result);
            int stride=expected.getVertexSize();
            for(int i=0;i<count;i++){
                int offset=i*stride;
                if((vertices.get(offset+12)&255)!=255||(vertices.get(offset+13)&255)!=255||(vertices.get(offset+14)&255)!=255)
                    throw new AssertionError("No directional dimming or vertex tint on the lens");
                int light=on?240:0;
                if((vertices.getShort(offset+stride-4)&65535)!=light||(vertices.getShort(offset+stride-2)&65535)!=light)
                    throw new AssertionError("Lit lenses must be full-bright; off lenses retain world light");
            }
            // Release test native buffers in both API versions.
            try{result.getClass().getMethod("close").invoke(result);}catch(NoSuchMethodException old){result.getClass().getMethod("release").invoke(result);}
            for(Object n:nativeBuffers)n.getClass().getMethod("close").invoke(n);
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(TrafficLightColor color:List.of(TrafficLightColor.NONE,TrafficLightColor.RED,TrafficLightColor.YELLOW,TrafficLightColor.GREEN)) {
            Buffers buffers=new Buffers();
            TrafficLightTextureManager.render(new RenderContext<>(null,new PoseStack(),buffers,0,0,0),null,TrafficLightIcon.NONE,color,0);
            buffers.verify(color!=TrafficLightColor.NONE);
        }
        System.out.println("PASS: actual native vertex buffers accept complete lit/off bulb geometry and the unshaded full-bright material format for all three signal colors.");
    }
}
