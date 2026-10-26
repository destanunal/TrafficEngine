package com.destan.trafficengine.network.packets.cts;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightPacket extends NetworkPacketData {

    private static final String NBT_POS = "Pos";
    private static final String NBT_ENABLED_COLORS = "EnabledColors";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_MODEL = "Model";
    private static final String NBT_ICON = "Icon";
    private static final String NBT_CONTROL_TYPE = "ControlType";
    private static final String NBT_COLORS = "Colors";
    private static final String NBT_ID = "Id";
    private static final String NBT_ADDITIONAL_STOP_IDS = "AdditionalPedestrianStopIds";
    private static final String NBT_SCHEDULED = "IsScheduled";

    private BlockPos pos;
    private int signalFace;
    public TrafficLightPacket withFace(int face){signalFace=face==1?1:0;return this;}
    private Collection<TrafficLightColor> enabledColors;
    private TrafficLightType type;
    private TrafficLightModel model;
    private TrafficLightIcon icon;
    private TrafficLightControlType controlType;
    private TrafficLightColor[] colors;
    private int phaseId;
    private Collection<Integer> additionalPedestrianStopIds = List.of();
    private boolean scheduleEnabled;
    private com.destan.trafficengine.data.PixelPattern mask = com.destan.trafficengine.data.PixelPattern.EMPTY;
    private com.destan.trafficengine.data.SignalSymbols signalSymbols;
    public TrafficLightPacket withSymbols(com.destan.trafficengine.data.SignalSymbols value) { signalSymbols=value;return this; }
    public TrafficLightPacket withMask(com.destan.trafficengine.data.PixelPattern value) { mask = value; return this; }

    public TrafficLightPacket(PacketStatus status) {
        super(status);
    }

    public TrafficLightPacket(BlockPos pos, Collection<TrafficLightColor> enabledColors, TrafficLightType type, TrafficLightModel model, TrafficLightIcon icon, TrafficLightControlType controlType, TrafficLightColor[] colorSlots, int phaseId, Collection<Integer> additionalPedestrianStopIds, boolean scheduleEnabled) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.enabledColors = enabledColors;
        this.type = type;
        this.model = model;
        this.icon = icon;
        this.controlType = controlType;
        this.colors = colorSlots;
        this.phaseId = phaseId;
        this.additionalPedestrianStopIds = List.copyOf(additionalPedestrianStopIds);
        this.scheduleEnabled = scheduleEnabled;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
        if(signalFace!=0)nbt.putByte("SignalFace",(byte)signalFace);
        ListTag enabledColorsList = new ListTag();
        for (TrafficLightColor color : enabledColors) {
            enabledColorsList.add(ByteTag.valueOf(color.getGroupIndex()));
        }
        nbt.put(NBT_ENABLED_COLORS, enabledColorsList);
        nbt.putByte(NBT_TYPE, type.getIndex());
        nbt.putByte(NBT_MODEL, model.getLightsCount());
        nbt.putByte(NBT_ICON, icon.getIndex());
        nbt.putByte(NBT_CONTROL_TYPE, controlType.getIndex());
        ListTag colorsList = new ListTag();
        for (TrafficLightColor color : colors) {
            colorsList.add(ByteTag.valueOf(color.getGroupIndex()));
        }
        nbt.put(NBT_COLORS, colorsList);
        nbt.putInt(NBT_ID, phaseId);
        nbt.putIntArray(NBT_ADDITIONAL_STOP_IDS, additionalPedestrianStopIds.stream().mapToInt(Integer::intValue).toArray());
        nbt.putBoolean(NBT_SCHEDULED, scheduleEnabled);
        if (!mask.isEmpty()) nbt.putLongArray("custom_mask", mask.bits());
        if(signalSymbols!=null)signalSymbols.write(nbt);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
        signalFace=nbt.getByte("SignalFace")==1?1:0;
        this.type = TrafficLightType.getTypeByIndex(nbt.getByte(NBT_TYPE));
        this.enabledColors = nbt.getList(NBT_ENABLED_COLORS, Tag.TAG_BYTE).stream().map(x -> TrafficLightColor.getColorByGroupIndex(((ByteTag)x).getAsByte(), type)).toList();
        this.model = TrafficLightModel.getModelByLightsCount(nbt.getByte(NBT_MODEL));
        this.icon = TrafficLightIcon.getIconByIndex(nbt.getByte(NBT_ICON));
        this.controlType = TrafficLightControlType.getControlTypeByIndex(nbt.getByte(NBT_CONTROL_TYPE));
        this.colors = nbt.getList(NBT_COLORS, Tag.TAG_BYTE).stream().map(x -> TrafficLightColor.getColorByGroupIndex(((ByteTag)x).getAsByte(), type)).toArray(TrafficLightColor[]::new);
        this.phaseId = nbt.getInt(NBT_ID);
        this.additionalPedestrianStopIds = Arrays.stream(nbt.getIntArray(NBT_ADDITIONAL_STOP_IDS)).limit(8).boxed().toList();
        this.scheduleEnabled = nbt.getBoolean(NBT_SCHEDULED);
        mask = com.destan.trafficengine.data.PixelPattern.of(nbt.getLongArray("custom_mask"));
        signalSymbols=nbt.contains("SignalSymbols")?com.destan.trafficengine.data.SignalSymbols.read(nbt):null;
    }
    
    
    public static void handle(TrafficLightPacket packet, NetworkPacketContext context) {
        ServerPlayer player = (ServerPlayer)context.getPlayer();
        if (player != null) {
            Level level = player.level();
            if (level.isLoaded(packet.pos) && !player.isSpectator()
                    && player.distanceToSqr(packet.pos.getX()+.5, packet.pos.getY()+.5, packet.pos.getZ()+.5) <= 64
                    && level.mayInteract(player, packet.pos)
                    && player.mayUseItemAt(packet.pos, net.minecraft.core.Direction.UP, player.getMainHandItem())
                    && level.getBlockState(packet.pos).getBlock() instanceof TrafficLightBlock
                    && !(level.getBlockState(packet.pos).getBlock() instanceof com.destan.trafficengine.block.WarningLightBlock)) {
                if (com.destan.trafficengine.util.SignalFaces.get(level,packet.pos,packet.signalFace) instanceof TrafficLightBlockEntity blockEntity) {
                    blockEntity.setRunning(packet.scheduleEnabled);
                    blockEntity.setPhaseId(packet.phaseId);
                    blockEntity.setAdditionalPedestrianStopIds(packet.additionalPedestrianStopIds);
                    blockEntity.setControlType(packet.controlType);
                    blockEntity.setIcon(packet.icon);
                    blockEntity.setCustomMask(packet.mask);
                    if(packet.signalSymbols!=null)blockEntity.setSignalSymbols(packet.signalSymbols);
                    blockEntity.setColorSlots(packet.colors);
                    blockEntity.enableOnlyColors(packet.enabledColors);
                    blockEntity.setType(packet.type);
                }
                BlockState state = level.getBlockState(packet.pos);
                if(packet.signalFace==1 && !(state.getBlock() instanceof com.destan.trafficengine.block.DoubleTrafficLightBlock))return;
                var modelProperty=packet.signalFace==1?com.destan.trafficengine.block.DoubleTrafficLightBlock.BACK_MODEL:TrafficLightBlock.MODEL;
                level.setBlockAndUpdate(packet.pos, state.setValue(modelProperty, packet.model));
                level.blockEntityChanged(packet.pos);
            }
        };
    }
}
