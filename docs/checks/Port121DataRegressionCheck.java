import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PixelPattern;
import com.destan.trafficengine.util.ItemData;
import java.lang.reflect.Constructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Exercises the 1.21 copy-on-write item component and registry-aware stencil persistence. */
public final class Port121DataRegressionCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        PixelPattern pattern = PixelPattern.of(new long[]{-1L, 0x12345L, 4L, Long.MIN_VALUE});
        ItemStack brush = new ItemStack(Items.STICK);
        CompoundTag tag = ItemData.getOrCreate(brush);
        tag.putLongArray("custom_road_pattern", pattern.bits());
        tag.putBoolean("custom_road_enabled", true); tag.putInt("paint", 53);
        ItemData.set(brush, tag);
        ItemStack copied = brush.copy();
        ItemData.edit(brush, nbt -> nbt.putInt("paint", 52));
        check(ItemData.get(brush).getInt("paint") == 52, "Painting writes through the 1.21 component");
        check(ItemData.get(copied).getInt("paint") == 53, "Editing a brush cannot mutate an ItemStack copy");
        check(PixelPattern.of(ItemData.get(brush).getLongArray("custom_road_pattern")).equals(pattern), "Paint consumption preserves the stencil");
        CompoundTag detached = ItemData.get(brush); detached.remove("custom_road_pattern");
        check(ItemData.get(brush).contains("custom_road_pattern"), "Read snapshots remain detached from the component");

        Constructor<ColoredBlockEntity> ctor = ColoredBlockEntity.class.getDeclaredConstructor(BlockEntityType.class, BlockPos.class, BlockState.class);
        ctor.setAccessible(true);
        ColoredBlockEntity road = ctor.newInstance(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        CompoundTag saved = new CompoundTag();
        saved.putLongArray(ColoredBlockEntity.CUSTOM_ROAD_PATTERN, pattern.bits());
        road.loadAdditional(saved, RegistryAccess.EMPTY);
        check(road.getCustomRoadPattern().equals(pattern) && road.getStrokeVertices().length > 0, "Registry-aware load reconstructs custom geometry");
        CompoundTag update = road.getUpdateTag(RegistryAccess.EMPTY);
        check(PixelPattern.of(update.getLongArray(ColoredBlockEntity.CUSTOM_ROAD_PATTERN)).equals(pattern), "Update NBT retains all stencil bits");
        check(!update.contains("road_stroke"), "Compact stencil data avoids duplicating generated geometry in NBT");
        road.loadAdditional(new CompoundTag(), RegistryAccess.EMPTY);
        check(road.getCustomRoadPattern().isEmpty() && road.getStrokeVertices().length == 0, "Clearing a stencil cannot leave stale geometry");
        System.out.println("PASS: 1.21 item component copies, paint preservation, registry-aware stencil save/load and geometry clearing.");
    }
}
