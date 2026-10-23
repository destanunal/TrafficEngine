package com.destan.trafficengine.item;
import com.destan.trafficengine.util.ItemData;

import java.util.List;

import net.minecraft.network.chat.Component;
import com.destan.trafficengine.Constants;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.data.RoadPatternNumbers;
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
        ItemData.set(stack, nbt);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                RoadPainting.clearSelection(nbt);
                ItemData.set(stack, nbt);
                player.getInventory().setChanged();
                player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.selection_cleared"), true);
            }
        } else if (level.isClientSide) {
            ClientWrapper.showPaintBrushScreen(nbt.getInt(NBT_PATTERN), PaintColor.getByIndex(nbt.getInt(NBT_COLOR)),
                RoadPainting.style(stack), nbt.getBoolean(RoadPainting.BATCH), RoadPaintPlan.normalizeWidth(nbt.getInt(RoadPainting.WIDTH)),
                RoadPainting.dashGap(stack), RoadPainting.position(stack), RoadPainting.areaStyle(stack), RoadPainting.areaShape(stack), RoadPainting.pathShape(stack), hand);
        }

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, context, list, flag);

        if (ItemData.has(stack)) {
            PaintColor paintColor = PaintColor.getByIndex(ItemData.get(stack).getInt(NBT_COLOR));
            String color = paintColor.getValueTranslation().getString();

            if (ItemData.get(stack).getBoolean("custom_road_enabled")) list.add(Component.translatable("gui.trafficengine.stencil.custom").withStyle(ChatFormatting.GRAY));
            else list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.pattern", "§f" + RoadPatternNumbers.displayNumber(ItemData.get(stack).getInt(NBT_PATTERN))).withStyle(ChatFormatting.GRAY));
            if (ItemData.get(stack).getInt(NBT_PAINT) == 0) {
                list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.color", Component.translatable("item.trafficengine.paint_brush.tooltip.color_empty")).withStyle(ChatFormatting.GRAY));
            } else {
                list.add(Component.translatable("item.trafficengine.paint_brush.tooltip.color", Component.literal(color).withStyle(Style.EMPTY.applyFormat(ChatFormatting.WHITE).withColor(paintColor.getTextureColor().getAsARGB()))).withStyle(ChatFormatting.GRAY));
            }
        }

        if (ItemData.has(stack)) {
            if (RoadPainting.areaStyle(stack)>0) {
                list.add(Component.translatable("gui.trafficengine.paint_brush.area.title").withStyle(ChatFormatting.AQUA));
                list.add(Component.translatable("gui.trafficengine.paint_brush.area.style."+RoadPainting.areaStyle(stack)).withStyle(ChatFormatting.AQUA));
            } else {
                if (ItemData.get(stack).getBoolean(RoadPainting.BATCH)) list.add(Component.translatable("gui.trafficengine.paint_brush.mode.batch").withStyle(ChatFormatting.AQUA));
                if (RoadPainting.style(stack) >= 0) list.add(Component.translatable("gui.trafficengine.paint_brush.connection." + (RoadPainting.style(stack) + 1)).withStyle(ChatFormatting.AQUA));
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

        if (ItemData.has(stack)) {
            nbt = ItemData.get(stack);
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
        ItemData.set(stack, nbt);

        BlockPos pos = pContext.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = pContext.getPlayer();

        if (player != null && RoadPainting.isRoad(state)) {
            var custom = nbt.getBoolean("custom_road_enabled") ? com.destan.trafficengine.data.PixelPattern.of(nbt.getLongArray(ColoredBlockEntity.CUSTOM_ROAD_PATTERN)) : com.destan.trafficengine.data.PixelPattern.EMPTY;
            boolean copyCustom = player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof ColoredBlockEntity colored && !colored.getCustomRoadPattern().isEmpty();
            if (!custom.isEmpty() || copyCustom) {
                InteractionResult result = CustomRoadPainting.use(pContext, custom);
                if (result != InteractionResult.PASS) return result;
            }
        }
        if (player != null && player.isShiftKeyDown() && state.getBlock() instanceof RoadBlock) {
            if (state.getBlock() instanceof com.destan.trafficengine.block.ConnectedRoadBlock
                    || state.getBlock() instanceof com.destan.trafficengine.block.ConnectedRoadSlopeBlock) {
                if (!level.isClientSide) {
                    nbt.putBoolean("custom_road_enabled", false);
                    nbt.putInt(RoadPainting.AREA_STYLE,0);
                    nbt.putInt(RoadPainting.STYLE, state.getValue(com.destan.trafficengine.block.ConnectedRoadBlock.STYLE).ordinal());
                    if (level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
                        nbt.putInt(NBT_COLOR, colored.getMarkingColor().getIndex());
                        nbt.putInt(RoadPainting.DASH_GAP,colored.getRoadDashGap());
                        int position=RoadMarkingMesh.automaticPosition(state,colored);
                        if (position>=0) nbt.putInt(RoadPainting.POSITION,position);
                    }
                    RoadPainting.clearSelection(nbt); ItemData.set(stack, nbt); player.getInventory().setChanged();
                    player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.copied"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            int marker = path.lastIndexOf("_pattern_");
            if (marker >= 0) {
                try {
                    int pattern = Integer.parseInt(path.substring(marker + "_pattern_".length()));
                    if (pattern >= 0 && pattern < Constants.MAX_ASPHALT_PATTERNS
                            && level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
                        PaintColor color = colored.getMarkingColor();
                        if (color == PaintColor.NONE) color = colored.getColor();
                        if (color == PaintColor.NONE) color = PaintColor.WHITE;
                        if (!level.isClientSide) {
                            nbt.putInt(NBT_PATTERN, pattern);
                            nbt.putInt(RoadPainting.STYLE, -1);
                            nbt.putBoolean("custom_road_enabled", false);
                    nbt.putInt(RoadPainting.AREA_STYLE,0);
                            RoadPainting.clearSelection(nbt);
                            nbt.putInt(NBT_COLOR, color.getIndex());
                            ItemData.set(stack, nbt);
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

        if (nbt.getBoolean(RoadPainting.BATCH) && RoadPainting.pathShape(stack)>0 && RoadPainting.areaStyle(stack)==0
                && !(state.getBlock() instanceof PaintBucketBlock)) return RoadPainting.use(pContext);
        if (RoadPainting.areaStyle(stack)>0 && !(state.getBlock() instanceof PaintBucketBlock)) {
            return RoadPainting.use(pContext);
        }
        if (RoadPainting.isRoad(state) && (nbt.getBoolean(RoadPainting.BATCH) || RoadPainting.style(stack) >= 0)) {
            return RoadPainting.use(pContext);
        }

        if (nbt.getInt(NBT_PAINT) <= 0) {
            return InteractionResult.FAIL;
        }

        if (state.getBlock() instanceof PaintBucketBlock) {
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.SUCCESS;
        } else {
            if (state.getBlock() instanceof IPaintableBlock block) {
                var undo = RoadPaintUndo.begin(level, player, stack, List.of(pos));
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
                        this.removePaint(player, stack, nbt);
                        res = InteractionResult.SUCCESS;
                    }
                    RoadPaintUndo.finish(undo);
                    return res;
                }

                InteractionResult res = block.onSetColor(pContext);
                if (res == InteractionResult.CONSUME) {
                    this.removePaint(player, stack, nbt);
                    res = InteractionResult.SUCCESS;
                }
                RoadPaintUndo.finish(undo);
                return res;
            }
        }
        return InteractionResult.PASS;
    }

    private void removePaint(Player player, ItemStack stack, CompoundTag nbt) {
        if (!player.isCreative()) {
            nbt.putInt(NBT_PAINT, nbt.getInt(NBT_PAINT) - 1);
            ItemData.set(stack, nbt);
        }
    }
}
