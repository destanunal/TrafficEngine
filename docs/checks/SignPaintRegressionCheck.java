import com.destan.trafficengine.block.entity.HouseNumberSignBlockEntity;
import com.destan.trafficengine.block.entity.StreetSignBlockEntity;
import com.destan.trafficengine.block.entity.SyncedBlockEntity;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.client.lighting.RgbLightMath;
import com.destan.trafficengine.data.PaintColor;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;

/** Runs actual paint packet/load methods with a recording level, without a game or mod JAR. */
public final class SignPaintRegressionCheck {
    private static final Unsafe UNSAFE;
    private static SyncedBlockEntity active;
    private static PaintColor expected;
    private static int updates;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static int abgr(int argb) { return (argb & 0xFF00FF00) | (argb & 255) << 16 | (argb >>> 16 & 255); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void recordUpdate(BlockPos pos, int flags) {
        check(((IColorBlockEntity) active).getColor() == expected, "Mesh rebuild must see the new colour");
        check(pos.equals(active.getBlockPos()) && (flags & 2) != 0, "Invalidate only this sign on the client");
        updates++;
    }

    private static Level recordingLevel(boolean client) throws Throwable {
        ClassWriter writer = new ClassWriter(0);
        String name = "RecordingSignLevel" + (client ? "Client" : "Server");
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/world/level/Level", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "sendBlockUpdated",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 1); method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "SignPaintRegressionCheck", "recordUpdate", "(Lnet/minecraft/core/BlockPos;I)V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(2, 5); method.visitEnd(); writer.visitEnd();
        Level level = (Level) UNSAFE.allocateInstance(MethodHandles.lookup().defineClass(writer.toByteArray()));
        UNSAFE.putBoolean(level, UNSAFE.objectFieldOffset(Level.class.getDeclaredField("isClientSide")), client);
        return level;
    }

    public static void main(String[] args) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Level client = recordingLevel(true), server = recordingLevel(false);
        int transitions = 0;
        for (Class<? extends SyncedBlockEntity> type : java.util.List.of(StreetSignBlockEntity.class, HouseNumberSignBlockEntity.class)) {
            var ctor = type.getDeclaredConstructor(BlockEntityType.class, BlockPos.class, BlockState.class);
            ctor.setAccessible(true);
            active = ctor.newInstance(BlockEntityType.SIGN, new BlockPos(3, 64, -7), net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
            active.setLevel(client);
            // Includes the reported blue -> yellow and red -> blue changes.
            for (PaintColor paint : new PaintColor[]{PaintColor.RED, PaintColor.BLUE, PaintColor.YELLOW,
                    PaintColor.BLUE, PaintColor.RED, PaintColor.NONE, PaintColor.WHITE, PaintColor.BLACK}) {
                CompoundTag tag = active.getUpdateTag(RegistryAccess.EMPTY); tag.putInt("color", paint.getIndex());
                tag.putString("line0", "Test street"); expected = paint; updates = 0;
                active.loadWithComponents(tag.copy(), RegistryAccess.EMPTY);
                check(updates == 1, "A new paint packet must rebuild the mesh exactly once");
                check(active.getUpdateTag(RegistryAccess.EMPTY).getInt("color") == paint.getIndex(), "Paint must survive save/reload");
                active.loadWithComponents(tag.copy(), RegistryAccess.EMPTY);
                check(updates == 1, "Repeated packet must not rebuild unchanged geometry");
                tag.putString("line0", "Another street"); active.loadWithComponents(tag, RegistryAccess.EMPTY);
                check(updates == 1, "Text-only changes must not rebuild the painted model");
                transitions++;
            }
            active.setLevel(server); updates = 0;
            CompoundTag changed = active.getUpdateTag(RegistryAccess.EMPTY); changed.putInt("color", PaintColor.YELLOW.getIndex());
            active.loadWithComponents(changed, RegistryAccess.EMPTY); check(updates == 0, "Server loads must not schedule client mesh work");
            active.setLevel(null); active.loadWithComponents(changed, RegistryAccess.EMPTY);
        }
        int colors = 0;
        Random random = new Random(182);
        for (PaintColor paint : PaintColor.values()) for (int sample = 0; sample < 256; sample++) {
            int argb = (random.nextInt(256) << 24) | (paint.getTextureColor().getAsARGB() & 0xFFFFFF);
            float r = random.nextFloat(), g = random.nextFloat(), b = random.nextFloat();
            int tinted = (argb & 0xFF000000) | (Math.round((argb >>> 16 & 255) * r) << 16)
                | (Math.round((argb >>> 8 & 255) * g) << 8) | Math.round((argb & 255) * b);
            check(RgbLightMath.tintAbgr(abgr(argb), r, g, b) == abgr(tinted),
                "Embeddium must tint the same RGB channels as vanilla and preserve alpha");
            colors++;
        }
        System.out.println("PASS: " + transitions + " sign paint transitions; duplicate/text/server packets stay idle; "
            + colors + " colour checks of Embeddium-compatible ABGR conversion.");
    }
}
