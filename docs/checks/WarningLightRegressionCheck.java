import com.destan.trafficengine.data.FlashSequence;
import com.destan.trafficengine.data.WarningLightPattern;
import com.destan.trafficengine.data.WarningLightSettings;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.packets.cts.WarningLightPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Regression for warning data being confused with inherited paint data. */
public final class WarningLightRegressionCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        CompoundTag mixed = new CompoundTag(); mixed.putInt("color", -1); mixed.putInt("appearance_primary", -1);
        mixed.putInt("warning_primary", 0xFFEE2200); mixed.putInt("warning_secondary", 0xFF2266FF);
        mixed.putString("flash_steps", "12"); mixed.putInt("flash_interval", 10); mixed.putBoolean("warning_redstone", true);
        WarningLightSettings read = WarningLightSettings.read(mixed);
        check(read.redstone() && read.primary() == 0xFFEE2200 && read.secondary() == 0xFF2266FF, "Paint data must not replace warning colour or redstone mode");
        int checks = 0;
        for (int lamps = 1; lamps <= 2; lamps++) for (boolean horizontal : new boolean[]{false,true}) for (boolean redstone : new boolean[]{false,true})
            for (int color : new int[]{0,0xFFFFFF,0xEE2200,0x2266FF,0xFFAA00}) for (WarningLightPattern pattern : WarningLightPattern.values()) {
                FlashSequence sequence = new FlashSequence(pattern.steps(lamps), 10);
                check(FlashSequence.valid(sequence.steps()), "Preset sequence bounds");
                WarningLightSettings settings = new WarningLightSettings(color, ~color, sequence, redstone);
                CompoundTag saved = new CompoundTag(); settings.write(saved);
                check(settings.equals(WarningLightSettings.read(saved.copy())), "Saved warning settings must round-trip");
                WarningLightPacket sent = new WarningLightPacket(new BlockPos(3,64,-9), settings, lamps, horizontal);
                WarningLightPacket received = new WarningLightPacket(PacketStatus.EMPTY); received.deserializeNbt(sent.serializeNbt());
                CompoundTag expected = sent.serializeNbt(), actual = received.serializeNbt();
                check(expected.equals(actual), "Packet must retain colours, redstone, vertical orientation and count");
                check(WarningLightSettings.read(actual.getCompound("Data")).redstone() == redstone, "Redstone selection must survive reopening");
                checks++;
            }
        WarningLightSettings defaults = WarningLightSettings.read(new CompoundTag());
        check(defaults.primary() == 0xFFFFAA00 && !defaults.redstone(), "Legacy defaults");
        check(defaults.sequence().steps().equals("10") && defaults.sequence().stepTicks() == 40,
            "New warning lights must blink with 2000 ms per step");
        check(defaults.sequence().mask(0) == 1 && defaults.sequence().mask(39) == 1
            && defaults.sequence().mask(40) == 0 && defaults.sequence().mask(80) == 1, "Two-second on/off timing");
        CompoundTag existing = new CompoundTag(); existing.putString("flash_steps", "12"); existing.putInt("flash_interval", 10);
        check(WarningLightSettings.read(existing).sequence().stepTicks() == 10, "Existing saved timing must remain unchanged");
        for (int tick=0;tick<400;tick++) {
            int mask = new FlashSequence(WarningLightPattern.ALTERNATE.steps(2),10).mask(tick);
            check(mask == 1 || mask == 2, "Alternating preset keeps exactly one lamp on");
        }
        check(WarningLightPattern.find("1020",2)==WarningLightPattern.ALTERNATE,"Legacy alternating pattern remains recognized");
        System.out.println("PASS: " + checks + " warning save/packet combinations; inherited-paint regression, redstone, orientation and presets.");
    }
}
