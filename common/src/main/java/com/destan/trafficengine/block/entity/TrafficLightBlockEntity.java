package com.destan.trafficengine.block.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.TrafficLightControllerBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.registry.ModBlockEntities;
import dev.architectury.utils.GameInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightBlockEntity extends ColoredBlockEntity {

    private static final String NBT_PHASE_ID = "phaseId";
    private static final String NBT_PENDING_PHASE_ID = "pendingPhaseId";
    private static final String NBT_ADDITIONAL_STOP_IDS = "additionalPedestrianStopIds";
    private static final String NBT_CONTROL_TYPE = "controlType";
    private static final String NBT_POWERED = "powered";
    private static final String NBT_TICKS = "ticks";
    private static final String NBT_TOTAL_TICKS = "totalTicks";
    private static final String NBT_RUNNING = "running";
    private static final String NBT_SCHEDULE = "schedule";
    private static final String NBT_PENDING_SCHEDULE = "pendingSchedule";
    private static final String NBT_ICON = "icon";
    private static final String NBT_TYPE = "type";
    private static final String NBT_COLOR_SLOTS = "colorSlots";
    private static final String NBT_ENABLED_COLORS = "enabledColors";
    private static final String NBT_TIMED_PHASE = "timedPhase";
    private static final String NBT_TIMED_PHASE_END = "timedPhaseEnd";
    private static final String NBT_TIMED_PHASE_BLINK = "timedPhaseBlink";
    private static final String NBT_TIMED_PHASE_REMAINING = "timedPhaseRemaining";
    private static final String NBT_TIMED_PHASE_RUNNING = "timedPhaseRunning";
    private static final String NBT_COLOR_PHASE_START = "colorPhaseStart";
    @Deprecated private static final String NBT_LINKED_TO = "linkedTo";

    // Properties
    private int phaseId = 0;
    private Integer pendingPhaseId = null;
    private final Set<Integer> additionalPedestrianStopIds = new LinkedHashSet<>();
    private TrafficLightControlType controlType = TrafficLightControlType.REMOTE;
    private TrafficLightIcon icon = TrafficLightIcon.NONE;
    private TrafficLightType type = TrafficLightType.NOCOUNTDOWN;
    // Önceki halinde 3 renk vardı, dördüncü (NONE) ekleyerek hafızayı 4'e çıkardık.
    private final TrafficLightColor[] colorSlots = new TrafficLightColor[] {
            TrafficLightColor.RED,
            TrafficLightColor.YELLOW,
            TrafficLightColor.GREEN,
            TrafficLightColor.NONE
    };
    private final Collection<TrafficLightColor> enabledColors = new ArrayList<>();
    private boolean powered = false;

    private TrafficLightSchedule schedule = new TrafficLightSchedule();
    private TrafficLightSchedule pendingSchedule = null;
    private int ticker = 0;
    private long totalTicks = 0;
    private boolean running = true;
    private boolean timedPhase = false;
    private long timedPhaseEnd = 0;
    private int timedPhaseBlink = 0;
    private int timedPhaseRemaining = 0;
    private boolean timedPhaseRunning = false;
    private long colorPhaseStart = -1;

    /** @deprecated Backwards compatibility only! */ @Deprecated private WorldLocation linkLocation = null;
    private boolean linkMigrated = false;

    protected TrafficLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TrafficLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_LIGHT_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider provider) {
        super.loadAdditional(compound, provider);

        this.phaseId = compound.getInt(NBT_PHASE_ID);
        this.pendingPhaseId = compound.contains(NBT_PENDING_PHASE_ID) ? compound.getInt(NBT_PENDING_PHASE_ID) : null;
        additionalPedestrianStopIds.clear();
        for (int id : compound.getIntArray(NBT_ADDITIONAL_STOP_IDS)) {
            if (additionalPedestrianStopIds.size() >= 8) break;
            if (id != phaseId && id >= -9999 && id <= 9999) additionalPedestrianStopIds.add(id);
        }
        this.controlType = TrafficLightControlType.getControlTypeByIndex(compound.getByte(NBT_CONTROL_TYPE));
        this.powered = compound.getBoolean(NBT_POWERED);
        this.ticker = compound.getInt(NBT_TICKS);
        this.totalTicks = compound.getLong(NBT_TOTAL_TICKS);
        this.running = compound.getBoolean(NBT_RUNNING);
        this.timedPhase = compound.getBoolean(NBT_TIMED_PHASE);
        this.timedPhaseEnd = compound.getLong(NBT_TIMED_PHASE_END);
        this.timedPhaseBlink = compound.getInt(NBT_TIMED_PHASE_BLINK);
        this.timedPhaseRemaining = compound.getInt(NBT_TIMED_PHASE_REMAINING);
        this.timedPhaseRunning = compound.getBoolean(NBT_TIMED_PHASE_RUNNING);
        this.colorPhaseStart = compound.contains(NBT_COLOR_PHASE_START) ? compound.getLong(NBT_COLOR_PHASE_START) : -1;
        this.schedule = new TrafficLightSchedule();
        this.schedule.fromNbt(compound.getCompound(NBT_SCHEDULE));
        this.pendingSchedule = null;
        if (compound.contains(NBT_PENDING_SCHEDULE)) {
            this.pendingSchedule = new TrafficLightSchedule();
            this.pendingSchedule.fromNbt(compound.getCompound(NBT_PENDING_SCHEDULE));
        }
        this.icon = TrafficLightIcon.getIconByIndex(compound.getByte(NBT_ICON));
        this.type = TrafficLightType.getTypeByIndex(compound.getByte(NBT_TYPE));
        int[] colorSlots = compound.getIntArray(NBT_COLOR_SLOTS);
        for (int i = 0; i < colorSlots.length && i < this.colorSlots.length; i++) {
            this.colorSlots[i] = TrafficLightColor.getColorByIndex((byte)colorSlots[i]);
        }
        this.enabledColors.clear();
        this.enabledColors.addAll(compound.getList(NBT_ENABLED_COLORS, Tag.TAG_BYTE).stream().map(x -> TrafficLightColor.getColorByIndex(((ByteTag)x).getAsByte())).toList());

        // backwards compatibility
        linkMigration(compound);
    }

    @SuppressWarnings("deprecation")
    private void linkMigration(CompoundTag nbt) {
        if (nbt.contains(NBT_LINKED_TO)) {
            TrafficEngine.LOGGER.warn("Traffic Light at position " + worldPosition.toShortString() + " contains deprecated link data. Trying to convert it.");
            linkLocation = new WorldLocation(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"), GameInstance.getServer().overworld());
            linkMigrated = false;
            return;
        }
        linkMigrated = true;        
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(NBT_PHASE_ID, phaseId);
        if (pendingPhaseId != null) tag.putInt(NBT_PENDING_PHASE_ID, pendingPhaseId);
        tag.putIntArray(NBT_ADDITIONAL_STOP_IDS, additionalPedestrianStopIds.stream().mapToInt(Integer::intValue).toArray());
        tag.putBoolean(NBT_POWERED, powered);
        tag.putByte(NBT_CONTROL_TYPE, controlType.getIndex());
        tag.putInt(NBT_TICKS, ticker);
        tag.putLong(NBT_TOTAL_TICKS, totalTicks);
        tag.putBoolean(NBT_RUNNING, running);
        tag.putBoolean(NBT_TIMED_PHASE, timedPhase);
        tag.putLong(NBT_TIMED_PHASE_END, timedPhaseEnd);
        tag.putInt(NBT_TIMED_PHASE_BLINK, timedPhaseBlink);
        tag.putInt(NBT_TIMED_PHASE_REMAINING, timedPhase ? getScheduleRemainingTicks() : timedPhaseRemaining);
        tag.putBoolean(NBT_TIMED_PHASE_RUNNING, timedPhaseRunning);
        tag.putLong(NBT_COLOR_PHASE_START, colorPhaseStart);
        tag.put(NBT_SCHEDULE, schedule.toNbt());
        if (pendingSchedule != null) tag.put(NBT_PENDING_SCHEDULE, pendingSchedule.toNbt());
        tag.putIntArray(NBT_COLOR_SLOTS, Arrays.stream(colorSlots).mapToInt(x -> x.getIndex()).toArray());
        tag.putByte(NBT_ICON, icon.getIndex());
        tag.putByte(NBT_TYPE, type.getIndex());
        ListTag enabledColorsTag = new ListTag();
        enabledColorsTag.addAll(enabledColors.stream().map(x -> ByteTag.valueOf(x.getIndex())).toList());
        tag.put(NBT_ENABLED_COLORS, enabledColorsTag);
        
        // backwards compatibility
        if (!linkMigrated && this.linkLocation != null) {
            tag.put(NBT_LINKED_TO, linkLocation.toNbt());
        }
        super.saveAdditional(tag, provider);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        
        // backwards compatibility       
        linkMigrationCheck(level, pos, state);

        if (controlType == TrafficLightControlType.OWN_SCHEDULE && pendingSchedule != null) {
            int cycle = schedule.getTotalDurationTicks();
            if (!running || cycle <= 0 || (ticker > 0 && Math.floorMod(ticker, cycle) == 0)) {
                schedule = pendingSchedule;
                pendingSchedule = null;
                ticker = 0;
                totalTicks = 0;
                timedPhase = false;
                notifyUpdate();
            }
        }

        if (this.getControlType() == TrafficLightControlType.OWN_SCHEDULE && schedule.hasPhaseTimings()) {
            if (running) {
                TrafficLightSchedule.PhaseState phase = schedule.getOwnPhaseAt(ticker);
                if (phase != null) applyTimedPhase(phase.colors(), phase.durationTicks(), phase.remainingTicks(), true, schedule.isBlinkAtEnd());
                else clearScheduledPhase();
                ticker++;
                totalTicks++;
                // Persist the own-schedule cursor without syncing NBT each tick.
                setChanged();
                if (!schedule.isLoop() && ticker >= schedule.getTotalDurationTicks()) setRunning(false);
            } else if (timedPhase && timedPhaseRunning) {
                timedPhaseRemaining = Math.max(0, (int)(timedPhaseEnd - level.getGameTime()));
                timedPhaseRunning = false;
                notifyUpdate();
            }
        } else if (running && this.getControlType() == TrafficLightControlType.OWN_SCHEDULE) {
            List<TrafficLightScheduleEntryData> stateData = schedule.shouldChange(ticker);

            if (stateData == null) { // OOB: End of schedule reached.
                ticker = 0;
                setChanged();
                if (!schedule.isLoop()) {
                    setRunning(false);
                }
                return;
            } else if (stateData.size() >= 0) {
                for (TrafficLightScheduleEntryData entry : stateData) {
                    Collection<TrafficLightColor> colors = entry.getEnabledColors();
                    if (colors != null) {
                        enableOnlyColors(colors);
                    }
                }
            }
            ticker++;
            totalTicks++;
            setChanged();
        }

        if (isPowered() && !level.hasNeighborSignal(pos)) {                
            this.setPowered(false);
        }
    }

    private void linkMigrationCheck(Level level, BlockPos pos, BlockState state) {
        if (linkMigrated) {
            return;
        }

        if (linkLocation == null) {
            linkMigrated = true;
            return;
        }

        if (level.isLoaded(linkLocation.getLocationBlockPos())) {
            if (level.getBlockState(linkLocation.getLocationBlockPos()).getBlock() instanceof TrafficLightControllerBlock &&
                level.getBlockEntity(linkLocation.getLocationBlockPos()) instanceof TrafficLightControllerBlockEntity blockEntity
            ) {
                blockEntity.addTrafficLightLocation(new WorldLocation(pos.getX(), pos.getY(), pos.getZ(), level.dimension().location()));
            }
            linkMigrated = true;
            return;
        }        
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TrafficLightBlockEntity blockEntity) {
        blockEntity.tick(level, pos, state);
    }


    /* GETTERS AND SETTERS */

    public void setPhaseId(int id) {
        this.phaseId = id;
        this.pendingPhaseId = null;
        additionalPedestrianStopIds.remove(id);
        notifyUpdate();
    }

    public void queuePhaseId(int id) {
        if (pendingPhaseId != null && pendingPhaseId == id) return;
        if (controlType != TrafficLightControlType.REMOTE || !timedPhase) {
            setPhaseId(id);
            return;
        }
        pendingPhaseId = id == phaseId ? null : id;
        notifyUpdate();
    }

    public void applyPendingPhaseId() {
        if (pendingPhaseId != null) setPhaseId(pendingPhaseId);
    }

    public int getPhaseIdForEditing() {
        return pendingPhaseId != null ? pendingPhaseId : phaseId;
    }

    public Set<Integer> getAdditionalPedestrianStopIds() {
        return Set.copyOf(additionalPedestrianStopIds);
    }

    public void setAdditionalPedestrianStopIds(Collection<Integer> ids) {
        additionalPedestrianStopIds.clear();
        for (int id : ids) {
            if (additionalPedestrianStopIds.size() >= 8) break;
            if (id != phaseId && id >= -9999 && id <= 9999) additionalPedestrianStopIds.add(id);
        }
        notifyUpdate();
    }

    public void setControlType(TrafficLightControlType controlType) {
        this.controlType = controlType;
        if (controlType != TrafficLightControlType.REMOTE) pendingPhaseId = null;
        if (controlType != TrafficLightControlType.OWN_SCHEDULE) pendingSchedule = null;
        this.timedPhase = false;
        notifyUpdate();
    }

    public void setSchedule(TrafficLightSchedule updated) {
        if (schedule.toNbt().equals(updated.toNbt())) {
            if (pendingSchedule != null) {
                pendingSchedule = null;
                notifyUpdate();
            }
            return;
        }
        if (controlType == TrafficLightControlType.OWN_SCHEDULE && running
                && schedule.getTotalDurationTicks() > 0) {
            pendingSchedule = updated;
            notifyUpdate();
            return;
        }
        pendingSchedule = null;
        schedule = updated;
        ticker = 0;
        totalTicks = 0;
        timedPhase = false;
        notifyUpdate();
    }

    public void setIcon(TrafficLightIcon icon) {
        this.icon = icon;
        notifyUpdate();
    }

    public boolean setColorToSlot(int index, TrafficLightColor color) {
        if (index < 0 || index >= colorSlots.length) {
            return false;
        }
        this.colorSlots[index] = color;
        notifyUpdate();
        return true;
    }

    public void setColorSlots(TrafficLightColor[] colorSlots) {
        for (int i = 0; i < colorSlots.length && i < getColorSlotCount(); i++) {
            this.colorSlots[i] = colorSlots[i];
        }
        notifyUpdate();
    }

    public void enableColors(Collection<TrafficLightColor> colors) {
        boolean colorsChanged = !enabledColors.containsAll(colors);
        timedPhase = false;
        this.enabledColors.addAll(colors);
        this.enabledColors.stream().distinct().toList();
        if (colorsChanged) recordColorPhaseStart();
        notifyUpdate();
    }

    public void enableOnlyColors(Collection<TrafficLightColor> colors) {
        boolean unchanged = this.enabledColors.size() == colors.size() && this.enabledColors.containsAll(colors);
        if (unchanged && !timedPhase) return;
        timedPhase = false;
        this.enabledColors.clear();
        this.enabledColors.addAll(colors);
        if (!unchanged) recordColorPhaseStart();
        notifyUpdate();
    }

    public void applyTimedPhase(Collection<TrafficLightColor> colors, int phaseDurationTicks, int remainingTicks, boolean isRunning, boolean blinkAtEnd) {
        if (level == null) return;
        long end = isRunning ? level.getGameTime() + remainingTicks : 0;
        boolean blinkColor = colors.size() == 1 && colors.contains(TrafficLightColor.GREEN);
        int blinkTicks = blinkAtEnd && blinkColor && phaseDurationTicks >= 80 ? 80 : 0;
        boolean colorsChanged = enabledColors.size() != colors.size() || !enabledColors.containsAll(colors);
        boolean timingChanged = !timedPhase || timedPhaseEnd != end || timedPhaseBlink != blinkTicks || timedPhaseRunning != isRunning;
        if (!colorsChanged && !timingChanged) return;
        enabledColors.clear();
        enabledColors.addAll(colors);
        if (colorsChanged) recordColorPhaseStart();
        timedPhase = true;
        timedPhaseEnd = end;
        timedPhaseBlink = blinkTicks;
        timedPhaseRemaining = remainingTicks;
        timedPhaseRunning = isRunning;
        notifyUpdate();
    }

    public boolean hasScheduleTiming() {
        return timedPhase;
    }

    public void clearScheduleTiming() {
        if (!timedPhase) return;
        timedPhase = false;
        notifyUpdate();
    }

    public void clearScheduledPhase() {
        if (!timedPhase && enabledColors.isEmpty()) return;
        if (!enabledColors.isEmpty()) recordColorPhaseStart();
        timedPhase = false;
        enabledColors.clear();
        notifyUpdate();
    }

    public void pauseTimedPhase() {
        if (!timedPhase || !timedPhaseRunning || level == null) return;
        timedPhaseRemaining = Math.max(0, (int)(timedPhaseEnd - level.getGameTime()));
        timedPhaseRunning = false;
        notifyUpdate();
    }

    public int getScheduleRemainingSeconds() {
        int ticksLeft = getScheduleRemainingTicks();
        if (ticksLeft < 0) return -1;
        return (ticksLeft + 19) / 20;
    }

    private int getScheduleRemainingTicks() {
        if (!timedPhase) return -1;
        return timedPhaseRunning && level != null
            ? (int)Math.max(0, timedPhaseEnd - level.getGameTime()) : timedPhaseRemaining;
    }

    public boolean isScheduleBlinkOff() {
        if (!timedPhase || !timedPhaseRunning || timedPhaseBlink <= 0 || level == null) return false;
        if (enabledColors.size() != 1 || !isColorEnabled(TrafficLightColor.GREEN, true)) return false;
        int ticksLeft = getScheduleRemainingTicks();
        // Use the same remaining time as the countdown and reject old saved six-second blink windows.
        if (ticksLeft <= 0 || getScheduleRemainingSeconds() > 4
            || ticksLeft > Math.min(timedPhaseBlink, 80)) return false;
        // One second off, one second on during the last four seconds.
        return ((ticksLeft - 1) / 20) % 2 == 1;
    }

    public void disableColors(Collection<TrafficLightColor> colors) {
        timedPhase = false;
        boolean colorsChanged = enabledColors.removeIf(colors::contains);
        if (colorsChanged) recordColorPhaseStart();
        notifyUpdate();
    }

    public void disableAll(Collection<TrafficLightColor> colors) {
        timedPhase = false;
        if (!enabledColors.isEmpty()) recordColorPhaseStart();
        enabledColors.clear();
        notifyUpdate();
    }

    private void recordColorPhaseStart() {
        if (level != null) colorPhaseStart = level.getGameTime();
    }

    public long getColorPhaseStart() {
        return colorPhaseStart;
    }

    public void setType(TrafficLightType type) {
        this.type = type;
        notifyUpdate();
    }


    

    public Collection<TrafficLightColor> getEnabledColors() {
        return this.enabledColors;
    }

    public boolean isColorEnabled(TrafficLightColor color, boolean allowSimilar) {
        for (TrafficLightColor enabled : enabledColors) {
            if ((allowSimilar && enabled.isSimilar(color)) || enabled == color) return true;
        }
        return false;
    }
    
    public int getPhaseId() {
        return this.phaseId;
    }

    public TrafficLightControlType getControlType() {
        return this.controlType;
    }

    public TrafficLightColor[] getColorSlots() {
        return this.colorSlots;
    }

    public TrafficLightIcon getIcon() {
        return this.icon;
    }

    public TrafficLightType getTLType() {
        return this.type;
    }

    public TrafficLightColor getColorOfSlot(int index) {
        return index >= 0 && index < colorSlots.length ? colorSlots[index] : TrafficLightColor.NONE;
    }

    public int getColorSlotCount() {
        return colorSlots.length;
    }

    public TrafficLightSchedule getSchedule() {
        return this.schedule;
    }

    public TrafficLightSchedule getScheduleForEditing() {
        return pendingSchedule != null ? pendingSchedule : schedule;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void setRunning(boolean b) {
        if (b && !this.running) {
            this.ticker = 0;
            this.totalTicks = 0;
        }

        this.running = b;
        notifyUpdate();
    }

    public void startSchedule(boolean forceRestart) {
        if (this.controlType == TrafficLightControlType.OWN_SCHEDULE && (forceRestart || !this.isFirstIteration())) {
            this.ticker = 0;
            this.totalTicks = 0;
            this.running = true;
        }
        notifyUpdate();
    }

    public void stopSchedule() {
        this.running = false;
        this.totalTicks = 0;
        this.ticker = 0;
        notifyUpdate();
    }

    public boolean isFirstIteration() {
        return this.totalTicks == this.ticker;
    }

    public void setPowered(boolean b) {
        this.powered = b;
        notifyUpdate();
    }

    public boolean isPowered() {
        return this.powered;
    }
    public int getTicker() {
        return this.ticker; // Eger bu satirda "ticker" kirmizi yanarsa, degiskenin adi farkli demektir (orn: tick, timer). O zaman adini ona gore degistirirsin.
    }
}
