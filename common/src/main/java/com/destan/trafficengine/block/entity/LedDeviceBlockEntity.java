package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.TrafficSignPostBlock;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.block.entity.SyncedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LedDeviceBlockEntity extends SyncedBlockEntity implements IColorBlockEntity {
    private int color = 0xFFFFFFFF;
    private int intervalTicks = 20;
    private String message = "";
    private String[] messageLines = {""};
    private long pixels = 0L;
    private final int[] pixelColors = new int[64];
    private boolean manualEnabled = true;
    private boolean controllerEnabled = false;
    private PaintColor paintColor = PaintColor.NONE;

    public LedDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LED_DEVICE_BLOCK_ENTITY.get(), pos, state);
        applyPixelColors(null);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        color = tag.contains("color") ? tag.getInt("color") : 0xFFFFFFFF;
        intervalTicks = tag.contains("interval") ? Math.max(2, tag.getInt("interval")) : 20;
        message = tag.contains("message") ? tag.getString("message") : "";
        if ("TRAFFICENGINE|BY DESTAN".equals(message)) message = "";
        messageLines = message.split("\\|", -1);
        pixels = tag.contains("pixels") ? tag.getLong("pixels") : 0L;
        applyPixelColors(tag.getIntArray("pixel_colors"));
        manualEnabled = !tag.contains("manual_enabled") || tag.getBoolean("manual_enabled");
        controllerEnabled = tag.getBoolean("controller_enabled");
        paintColor = tag.contains("paint_color") ? PaintColor.getByIndex(tag.getInt("paint_color")) : PaintColor.NONE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("color", color);
        tag.putInt("interval", intervalTicks);
        tag.putString("message", message);
        tag.putLong("pixels", pixels);
        tag.putIntArray("pixel_colors", pixelColors);
        tag.putBoolean("manual_enabled", manualEnabled);
        tag.putBoolean("controller_enabled", controllerEnabled);
        tag.putInt("paint_color", paintColor.getIndex());
        super.saveAdditional(tag, provider);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LedDeviceBlockEntity blockEntity) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            boolean postBelow = level.getBlockState(pos.below()).getBlock() instanceof TrafficSignPostBlock;
            boolean postAbove = level.getBlockState(pos.above()).getBlock() instanceof TrafficSignPostBlock;
            boolean postBehind = level.getBlockState(pos.relative(state.getValue(LedDeviceBlock.FACING).getOpposite())).getBlock() instanceof TrafficSignPostBlock;
            boolean postMounted = !state.getValue(LedDeviceBlock.WALL_MOUNTED) && (postBelow || postAbove || postBehind);
            if (state.getValue(LedDeviceBlock.POST_MOUNTED) != postMounted
                || state.getValue(LedDeviceBlock.POST_ABOVE) != postAbove
                || state.getValue(LedDeviceBlock.POST_SIDE) != (postBehind && !postAbove && !postBelow)) {
                level.setBlockAndUpdate(pos, state.setValue(LedDeviceBlock.POST_MOUNTED, postMounted)
                    .setValue(LedDeviceBlock.POST_ABOVE, !state.getValue(LedDeviceBlock.WALL_MOUNTED) && postAbove)
                    .setValue(LedDeviceBlock.POST_SIDE, !state.getValue(LedDeviceBlock.WALL_MOUNTED)
                        && postBehind && !postAbove && !postBelow));
            }
            blockEntity.setChanged();
            LedDeviceBlock.ensureDisplayParts(level, pos, level.getBlockState(pos));
        }
    }

    public boolean isActive() {
        return manualEnabled || controllerEnabled || (level != null && level.hasNeighborSignal(worldPosition));
    }

    public boolean isLit() {
        return isActive();
    }

    public void configure(int color, int intervalTicks, String message, long pixels, int[] pixelColors, boolean enabled) {
        this.color = 0xFF000000 | (color & 0xFFFFFF);
        this.intervalTicks = Mth.clamp(intervalTicks, 2, 1200);
        int slots = ((LedDeviceBlock)getBlockState().getBlock()).getDeviceType().getMessageSlots();
        int maxMessageLength = slots * 24 + Math.max(0, slots - 1);
        this.message = message == null ? "" : message.substring(0, Math.min(maxMessageLength, message.length()));
        this.messageLines = this.message.split("\\|", -1);
        this.pixels = pixels;
        applyPixelColors(pixelColors);
        this.manualEnabled = enabled;
        notifyUpdate();
    }

    private void applyPixelColors(int[] values) {
        for (int i = 0; i < pixelColors.length; i++) {
            // Legacy panels have only a global color and a bit mask.
            pixelColors[i] = 0xFF000000 | ((values != null && values.length == 64 ? values[i] : color) & 0xFFFFFF);
        }
    }

    public int[] getPixelColors() { return pixelColors.clone(); }
    public int getPixelColor(int index) { return pixelColors[index]; }
    public int getLedColor() { return color; }
    public int getIntervalTicks() { return intervalTicks; }
    public String getMessage() { return message; }
    public String[] getMessageLines() { return messageLines; }
    public long getPixels() { return pixels; }
    public boolean isManualEnabled() { return manualEnabled; }
    public void setManualEnabled(boolean value) { manualEnabled = value; notifyUpdate(); }
    @Override public PaintColor getColor() { return paintColor; }
    @Override public void setColor(PaintColor value) {
        paintColor = value;
        if (value != PaintColor.NONE) {
            color = value.getTextureColor().getAsARGB();
            applyPixelColors(null);
        }
        notifyUpdate();
    }
    public void setControllerEnabled(boolean value) {
        if (controllerEnabled != value) {
            controllerEnabled = value;
            notifyUpdate();
        }
    }
}
