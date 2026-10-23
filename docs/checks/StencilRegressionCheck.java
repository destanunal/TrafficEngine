import com.destan.trafficengine.data.FlashSequence;
import com.destan.trafficengine.data.PixelPattern;
import java.util.Arrays;
import java.util.Random;

/** Standalone checks; run with javac/java, without Minecraft or a packaged mod. */
public final class StencilRegressionCheck {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static int maxQuads;
    private static void verify(long[] bits) {
        PixelPattern pattern = PixelPattern.of(bits);
        boolean[] covered = new boolean[256];
        for (var rectangle : pattern.rectangles()) {
            check(rectangle.x() >= 0 && rectangle.y() >= 0 && rectangle.x() + rectangle.width() <= 16 && rectangle.y() + rectangle.height() <= 16, "Rectangle bounds");
            for (int y = rectangle.y(); y < rectangle.y() + rectangle.height(); y++) for (int x = rectangle.x(); x < rectangle.x() + rectangle.width(); x++) {
                check(!covered[y * 16 + x], "Overlapping rectangles"); covered[y * 16 + x] = true;
            }
        }
        maxQuads = Math.max(maxQuads, pattern.rectangles().size());
        for (int index = 0; index < 256; index++) check(covered[index] == ((bits[index >>> 6] & 1L << (index & 63)) != 0), "Lost or added pixel");
        for (int turn = 0; turn < 4; turn++) {
            float[] mesh = pattern.roadVertices(.753125F, turn);
            boolean[] raster = new boolean[256];
            for (int q = 0; q < mesh.length; q += 12) {
                float minX = 1, minZ = 1, maxX = 0, maxZ = 0;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float x = mesh[q + vertex * 3], y = mesh[q + vertex * 3 + 1], z = mesh[q + vertex * 3 + 2];
                    check(x >= 0 && x <= 1 && z >= 0 && z <= 1 && y == .753125F, "Rotated road vertex bounds/height");
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
                float ax = mesh[q+3]-mesh[q], az = mesh[q+5]-mesh[q+2], bx = mesh[q+6]-mesh[q], bz = mesh[q+8]-mesh[q+2];
                check(az*bx-ax*bz > 0, "Top face must face upwards");
                for (int y = Math.round(minZ*16); y < Math.round(maxZ*16); y++) for (int x = Math.round(minX*16); x < Math.round(maxX*16); x++) raster[y*16+x]=true;
            }
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
                int rx=x, ry=y;
                for (int t=0;t<turn;t++) { int previous=rx;rx=15-ry;ry=previous; }
                check(raster[ry*16+rx]==pattern.pixel(x,y), "Rotation must preserve stencil pixels");
            }
        }
        check(pattern.equals(PixelPattern.of(pattern.bits())), "Bit serialization round trip");
    }
    public static void main(String[] args) {
        check(PixelPattern.of(new long[3]) == PixelPattern.EMPTY, "Malformed payload");
        check(PixelPattern.of(null) == PixelPattern.EMPTY, "Missing legacy data");
        long[] full = {-1L,-1L,-1L,-1L}; verify(full);
        check(PixelPattern.of(full).rectangles().size() == 1, "Solid stencil must merge into one quad");
        long[] checker = new long[4];
        for (int y=0;y<16;y++) for (int x=0;x<16;x++) if ((x+y)%2==0) { int index=y*16+x;checker[index>>>6]|=1L<<(index&63); }
        verify(checker); check(PixelPattern.of(checker).rectangles().size()==128, "Checkerboard budget");
        for (int index=0;index<256;index++) { long[] single=new long[4];single[index>>>6]=1L<<(index&63);verify(single); }
        PixelPattern immutable=PixelPattern.of(full);full[0]=0;long[] exported=immutable.bits();exported[1]=0;
        check(immutable.pixel(0,0)&&immutable.pixel(0,4),"Stencil must not retain mutable caller arrays");
        Random random=new Random(19);
        for (int sample=0;sample<3000;sample++) { long[] bits=new long[4];for (int i=0;i<4;i++)bits[i]=random.nextLong();verify(bits); }
        check(!FlashSequence.valid("") && !FlashSequence.valid("1234") && !FlashSequence.valid("1".repeat(33)),"Invalid sequence rejection");
        FlashSequence alternate=new FlashSequence("1020",10);
        check(alternate.mask(0)==1 && alternate.mask(9)==1 && alternate.mask(10)==0 && alternate.mask(20)==2 && alternate.mask(40)==1,"Sequence timing");
        check(new FlashSequence("30",1).stepTicks()==2 && new FlashSequence("30",9999).stepTicks()==200,"Bounded timing");
        check(new FlashSequence("invalid",10).steps().equals(FlashSequence.DEFAULT.steps()),"Malformed saved data fallback");
        check(alternate.mask(Long.MAX_VALUE)>=0 && alternate.mask(-1)==0,"World-time bounds");
        long sum=0;for(int i=0;i<1_000_000;i++)sum+=alternate.mask(i);
        long started=System.nanoTime();for(int i=0;i<5_000_000;i++)sum+=alternate.mask(i);
        double ns=(System.nanoTime()-started)/5_000_000D;
        System.out.printf("PASS: 3,258 stencil cases × 4 rotations; max %d top quads; timing/bounds/immutability checks.%n",maxQuads);
        System.out.printf("Sequence arithmetic sample: %.1f ns/call, checksum %d (CPU sample, not an FPS measurement).%n",ns,sum);
    }
}
