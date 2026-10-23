import com.destan.trafficengine.client.TrafficLightTextureManager;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.client.gui.components.TESignalIcons;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.management.ManagementFactory;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Counts actual glow vertices without a game window, and samples render-call allocations. */
public final class ModernWarningRenderRegressionCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Vertices implements VertexConsumer, MultiBufferSource {
        int count, lightU, lightV, r, g, b;
        RenderType type;
        double minX, maxX, minY, maxY, minZ, maxZ;
        boolean inspect = true;
        void reset() { count = 0; minX=minY=minZ=Double.POSITIVE_INFINITY; maxX=maxY=maxZ=Double.NEGATIVE_INFINITY; }
        public VertexConsumer getBuffer(RenderType type) {
            if (this.type == null) this.type = type;
            else check(this.type == type, "Active lens reuses one cached render type");
            return this;
        }
        public VertexConsumer vertex(double x, double y, double z) {
            if (inspect) { minX=Math.min(minX,x); maxX=Math.max(maxX,x); minY=Math.min(minY,y); maxY=Math.max(maxY,y); minZ=Math.min(minZ,z); maxZ=Math.max(maxZ,z); }
            return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) { this.r=r; this.g=g; this.b=b; return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { lightU=u; lightV=v; return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { count++; }
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
    private static void icons() throws Exception {
        var field = TESignalIcons.class.getDeclaredField("ICONS"); field.setAccessible(true);
        float[][] icons = (float[][])field.get(null);
        check(icons.length == 8, "All selectable signal glyphs have neutral geometry");
        for (TrafficLightIcon icon : TrafficLightIcon.values()) {
            float[] points = icons[icon.ordinal()];
            if (icon == TrafficLightIcon.NONE) {
                check(points != null && points.length == 0, "No icon leaves its button empty");
                TESignalIcons.draw(null, icon, 0, 0, -1); // Empty choice must not access a render buffer.
                continue;
            }
            check(points != null && points.length > 0 && points.length % 8 == 0, "Complete cached icon quads");
            for (float p : points) check(Float.isFinite(p) && p >= 0 && p <= 16, "Glyph fits its 16px canvas");
            for (int i=0; i<points.length; i+=8) {
                float area=0;
                for (int j=0;j<4;j++) { int a=i+j*2,b=i+(j+1)%4*2; area+=points[a]*points[b+1]-points[b]*points[a+1]; }
                check(area <= .0001, "Icon quads face the GUI camera");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        icons();
        PoseStack pose = new PoseStack(); pose.translate(3, 4, 5);
        Vertices vertices = new Vertices(); vertices.reset();
        RenderContext<?> context = new RenderContext<>(null, pose, vertices, 0, 0, 0);
        TrafficLightTextureManager.renderWarningLens(context, 0xFF42A7DD);
        check(vertices.count == 4, "One active lamp submits one quad");
        check(vertices.r == 0x42 && vertices.g == 0xA7 && vertices.b == 0xDD, "The chosen lens colour keeps RGB channels");
        check((vertices.lightV<<16|vertices.lightU) == LightTexture.FULL_BRIGHT, "Only the active glow is emissive");
        check(vertices.minX == 3 && vertices.maxX == 7 && vertices.minY == 0 && vertices.maxY == 4, "The glow occupies the existing four-model-unit lens");
        check(Math.abs(vertices.minZ-6.002) < .00001 && vertices.minZ == vertices.maxZ, "Preserve the non-coplanar glow plane and pose");
        vertices.inspect=false;
        for (int i=0;i<200_000;i++) TrafficLightTextureManager.renderWarningLens(context, 0xFF42A7DD);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            long id=Thread.currentThread().getId(), before=bean.getThreadAllocatedBytes(id);
            for (int i=0;i<250_000;i++) TrafficLightTextureManager.renderWarningLens(context, 0xFF42A7DD);
            long bytes=bean.getThreadAllocatedBytes(id)-before;
            check(bytes<4096, "No allocations per glow render: "+bytes);
            System.out.println("Glow allocation sample: "+bytes+" bytes across 250,000 renders; not an FPS measurement.");
        }
        System.out.println("PASS: empty no-icon choice and seven neutral glyphs; one glow quad, stable material, colour channels, full-bright light and transformed lens coordinates.");
    }
}
