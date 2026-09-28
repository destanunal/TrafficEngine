package com.destan.trafficengine.item;
import com.destan.trafficengine.util.ItemData;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.client.tooltip.TrafficSignTooltip;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.data.TrafficSignData;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PatternCatalogueItem extends Item {

    @Deprecated private static final String NBT_LEGACY_PATTERNS = "patterns";
    private static final String NBT_SELECTED_INDEX = "SelectedIndex";
    private static final String NBT_TEXTURES = "TextureIds";


    private static final int MAX_SIGN_PATTERNS = 36;

    private static final Map<ItemStack, Optional<TooltipComponent>> tooltips = new WeakHashMap<>();

    public PatternCatalogueItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            ClientWrapper.showSignPatternSelectionScreen(stack);
        }

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    public int getMaxPatterns() {
        return MAX_SIGN_PATTERNS;
    }

    @SuppressWarnings("deprecation")
    protected static CompoundTag checkNbt(ItemStack stack) {
        CompoundTag nbt = ItemData.getOrCreate(stack);

        if (!nbt.contains(NBT_TEXTURES)) {            
            nbt.put(NBT_TEXTURES, new ListTag());
        }

        if (nbt.contains(NBT_LEGACY_PATTERNS)) {
            nbt.getList(NBT_LEGACY_PATTERNS, 10).stream().forEach(x -> {    
                nbt.getList(NBT_TEXTURES, 10).add(TrafficSignData.migrate((CompoundTag)x).toNbt());
            });
            nbt.remove(NBT_LEGACY_PATTERNS);
        }

        if (!nbt.contains(NBT_SELECTED_INDEX)) {
            nbt.putInt(NBT_SELECTED_INDEX, 0);
        }

        ItemData.set(stack, nbt);
        return nbt;
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack pStack) {
        Optional<TooltipComponent> tooltip = tooltips.computeIfAbsent(pStack, x -> createTooltip(x));
        tooltip.ifPresent(x -> ((TrafficSignTooltip)x).initAgeable());
        return tooltip;
    }

    private Optional<TooltipComponent> createTooltip(ItemStack stck) {
        final ItemStack stack = stck;
        WeakReference<ItemStack> stackReference = new WeakReference<>(stack);
        NonNullList<NamedTrafficSignTextureReference> nonnulllist = NonNullList.create();
        if (ItemData.has(stack)) {
            Arrays.stream(getStoredPatterns(stack)).forEach(nonnulllist::add);
        }
        return Optional.of(new TrafficSignTooltip(nonnulllist, getSelectedImageData(stack), getSelectedIndex(stack), () -> {
            ItemStack cachedStack = stackReference.get();
            if (cachedStack != null) {
                tooltips.remove(cachedStack);
            }
        }));
    }

    public NamedTrafficSignTextureReference getSelectedImageData(ItemStack stack) {
        return getSelectedPattern(stack);
    }

    protected static boolean indexInBounds(ItemStack stack, int index) {
        return index >= 0 && index < getStoredPatternCount(stack);
    }

    public static int getSelectedIndex(ItemStack stack) {
        return checkNbt(stack).getInt(NBT_SELECTED_INDEX);
    }

    public static short getStoredPatternCount(ItemStack stack) {
        return (short)(checkNbt(stack).getList(NBT_TEXTURES, 10).size());
    }

    public static NamedTrafficSignTextureReference getPatternAt(ItemStack stack, int index) {
        if (!indexInBounds(stack, index))
            return null;

        return NamedTrafficSignTextureReference.fromNbt(checkNbt(stack).getList(NBT_TEXTURES, 10).getCompound(index));
    }

    public static NamedTrafficSignTextureReference getSelectedPattern(ItemStack stack) {
        return getPatternAt(stack, getSelectedIndex(stack));
    }

    public static NamedTrafficSignTextureReference[] getStoredPatterns(ItemStack stack) {
        return checkNbt(stack).getList(NBT_TEXTURES, 10).stream().map(x -> {
            return NamedTrafficSignTextureReference.fromNbt((CompoundTag)x);
        }).toArray(NamedTrafficSignTextureReference[]::new);
    }

    public static boolean setPattern(ItemStack stack, NamedTrafficSignTextureReference pattern) {
        if (getStoredPatternCount(stack) >= ((PatternCatalogueItem)stack.getItem()).getMaxPatterns())
            return false;

        CompoundTag nbt = checkNbt(stack);
        ListTag tag = nbt.getList(NBT_TEXTURES, 10);
        tag.add(pattern.toNbt());
        ItemData.set(stack, nbt);
        setSelectedIndex(stack, tag.size() - 1);
        return true;
    }

    public static boolean replacePattern(ItemStack stack, NamedTrafficSignTextureReference pattern, int index) {
        if (getStoredPatternCount(stack) >= ((PatternCatalogueItem)stack.getItem()).getMaxPatterns())
            return false;

        CompoundTag nbt = checkNbt(stack);
        nbt.getList(NBT_TEXTURES, 10).set(index, pattern.toNbt());
        ItemData.set(stack, nbt);
        setSelectedIndex(stack, index);
        return true;
    }

    public static boolean removePatternAt(ItemStack stack, int index) {
        if (!indexInBounds(stack, index))
            return false;

        CompoundTag nbt = checkNbt(stack);
        nbt.getList(NBT_TEXTURES, 10).remove(index);
        ItemData.set(stack, nbt);
        int count = PatternCatalogueItem.getStoredPatternCount(stack);
        if (index >= count) {
            setSelectedIndex(stack, Math.max(0, count - 1));
        }
        return true;
    }

    public static void clearPatterns(ItemStack stack) {
        CompoundTag nbt = checkNbt(stack);
        nbt.getList(NBT_TEXTURES, 10).clear();

        if (nbt.contains(NBT_LEGACY_PATTERNS)) {
            nbt.getList(NBT_LEGACY_PATTERNS, 10).clear();
        }
        ItemData.set(stack, nbt);
    }

    public static void setSelectedIndex(ItemStack stack, int index) {
        CompoundTag nbt = checkNbt(stack);
        nbt.putInt(NBT_SELECTED_INDEX, Mth.clamp(index, -1, Math.max(0, PatternCatalogueItem.getStoredPatternCount(stack) - 1)));
        ItemData.set(stack, nbt);
    }
}
