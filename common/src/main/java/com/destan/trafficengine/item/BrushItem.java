package com.destan.trafficengine.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import com.destan.trafficengine.Constants;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.block.PaintBucketBlock;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.IPaintableBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BrushItem extends Item {

    public static final String NBT_PATTERN = "pattern";
    public static final String NBT_PAINT = "paint";
    public static final String NBT_COLOR = "color";

    private int paintAmount = 0;

    public BrushItem(Properties properties, int paintAmount) {
        super(properties.stacksTo(1));
        this.paintAmount = paintAmount;        
    }

    
    @Override
    public boolean canAttackBlock(BlockState state, Level worldIn, BlockPos pos, Player player) {
        if (player.isCreative()) {
            if (state.getBlock() instanceof IPaintableBlock block) {  
                block.onRemoveColor(state, worldIn, pos, player);
                return false;
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag nbt = checkNbt(stack);
        stack.setTag(nbt);

        if (level.isClientSide) {
            ClientWrapper.showPaintBrushScreen(nbt.getInt(NBT_PATTERN), PaintColor.getByIndex(nbt.getInt(NBT_COLOR)));
        }

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level player, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, player, list, flag);
        
        if (stack.hasTag()) {
            PaintColor paintColor = PaintColor.getByIndex(stack.getTag().getInt(NBT_COLOR));
            String color = paintColor.getValueTranslation().getString();
    
            list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.pattern", "§f" + stack.getTag().getInt(NBT_PATTERN)).withStyle(ChatFormatting.GRAY));
            if (stack.getTag().getInt(NBT_PAINT) == 0) {
                list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.color", Component.translatable("item.trafficengine.paint_brush.tooltip.color_empty")).withStyle(ChatFormatting.GRAY));
            } else {
                list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.color", Component.literal(color).withStyle(Style.EMPTY.applyFormat(ChatFormatting.WHITE).withColor(paintColor.getTextureColor().getAsARGB()))).withStyle(ChatFormatting.GRAY));
            }
        }

        list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
        
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        if(checkNbt(stack).getInt(NBT_PAINT) > 0)
            return true;
        else
            return false;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (checkNbt(stack).getInt(NBT_PAINT) * 13) / Constants.MAX_PAINT;
    }

    @Override
    public int getBarColor(ItemStack pStack) {
        return getColor(pStack).getTextureColor().getAsARGB();
    }

    public static CompoundTag checkNbt(ItemStack stack) {
        CompoundTag nbt;

        if (stack.hasTag()) {
            nbt = stack.getTag();
        } else {
            nbt = new CompoundTag();
            nbt.putInt(NBT_PAINT, 0);
            nbt.putInt(NBT_PATTERN, 0);
            nbt.putInt(NBT_COLOR, 0xFFFFFFFF);
        }

        return nbt;
    }

    public int getPaintAmount() {
        return this.paintAmount;
    }

    public static PaintColor getColor(ItemStack stack) {
        return PaintColor.getByIndex(checkNbt(stack).getInt(NBT_COLOR));
    }

    public static int getPatternId(ItemStack stack) {
        return checkNbt(stack).getInt(NBT_PATTERN);
    }

    public static int getPaint(ItemStack stack) {
        return checkNbt(stack).getInt(NBT_PAINT);
    }

    public int getMaxPaint() {
        return Constants.MAX_PAINT;
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        ItemStack stack = pContext.getItemInHand();
        CompoundTag nbt = checkNbt(stack);
        stack.setTag(nbt);

        BlockPos pos = pContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = pContext.getPlayer();

        if (player != null && player.isShiftKeyDown() && state.getBlock() instanceof RoadBlock) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            int marker = path.lastIndexOf("_pattern_");
            if (marker >= 0) {
                try {
                    int pattern = Integer.parseInt(path.substring(marker + "_pattern_".length()));
                    if (pattern > 0 && pattern < Constants.MAX_ASPHALT_PATTERNS
                            && level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
                        PaintColor color = colored.getMarkingColor();
                        if (color == PaintColor.NONE) color = colored.getColor();
                        if (color == PaintColor.NONE) color = PaintColor.WHITE;
                        if (!level.isClientSide) {
                            nbt.putInt(NBT_PATTERN, pattern);
                            nbt.putInt(NBT_COLOR, color.getIndex());
                            player.getInventory().setChanged();
                            player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.copied"), true);
                        }
                        return InteractionResult.sidedSuccess(level.isClientSide);
                    }
                } catch (NumberFormatException ignored) {
                    // Other road-like blocks may not have a numeric pattern suffix.
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (nbt.getInt(NBT_PAINT) <= 0) {
            return InteractionResult.FAIL;
        }

        if (state.getBlock() instanceof PaintBucketBlock) {
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.SUCCESS;
        } else {
            if (state.getBlock() instanceof IPaintableBlock block) {
                boolean matchingRoadMarking = state.getBlock() instanceof RoadBlock
                        && level.getBlockEntity(pos) instanceof ColoredBlockEntity coloredBlockEntity
                        && BrushItem.getPatternId(pContext.getItemInHand()) != 0
                        && coloredBlockEntity.getMarkingColor() == BrushItem.getColor(pContext.getItemInHand());
                boolean recolorRoadMarking = state.getBlock() instanceof RoadBlock
                        && level.getBlockEntity(pos) instanceof ColoredBlockEntity coloredBlockEntity
                        && BrushItem.getPatternId(pContext.getItemInHand()) != 0
                        && coloredBlockEntity.getMarkingColor() != BrushItem.getColor(pContext.getItemInHand());

                if (matchingRoadMarking || (!recolorRoadMarking
                        && level.getBlockEntity(pos) instanceof IColorBlockEntity blockEntity
                        && blockEntity.getColor() == PaintColor.getByIndex(nbt.getInt(NBT_COLOR)))) {
                    InteractionResult res = block.update(pContext);
                    if (res == InteractionResult.CONSUME) {
                        this.removePaint(player, nbt);
                        res = InteractionResult.SUCCESS;
                    }
                    return res;
                }

                InteractionResult res = block.onSetColor(pContext);
                if (res == InteractionResult.CONSUME) {
                    this.removePaint(player, nbt);
                    res = InteractionResult.SUCCESS;
                }
                return res;
            }
        }
        return InteractionResult.PASS;
    }

    private void removePaint(Player player, CompoundTag nbt) {
        if (!player.isCreative()) {                    
            nbt.putInt(NBT_PAINT, nbt.getInt(NBT_PAINT) - 1);
        }
    }
}
