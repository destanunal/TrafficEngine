package com.destan.trafficengine.block.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.destan.trafficengine.block.entity.SyncedBlockEntity;
import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightControllerBlockEntity extends SyncedBlockEntity {

    private record PhaseKey(int id, boolean crossing) {}

    private static final String NBT_TRAFFIC_LIGHT_LOCATIONS = "LinkedTrafficLights";
    private static final String NBT_TICKS = "ticks";
    private static final String NBT_TOTAL_TICKS = "totalTicks";
    private static final String NBT_POWERED = "powered";
    private static final String NBT_SCHEDULES = "schedules";
    private static final String NBT_PENDING_SCHEDULES = "pendingSchedules";
    private static final String NBT_RUNNING = "running";

    // Properties
    private List<TrafficLightSchedule> schedules = new ArrayList<>();
    private List<TrafficLightSchedule> pendingSchedules = new ArrayList<>();
    private int ticks = 0;
    private long totalTicks = 0;
    private boolean running = true;
    private boolean powered = false;
    private List<WorldLocation> trafficLightLocations = new ArrayList<>();

    protected TrafficLightControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TrafficLightControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_LIGHT_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider provider) {
        super.loadAdditional(compound, provider);

        this.ticks = compound.getInt(NBT_TICKS);
        this.running = compound.getBoolean(NBT_RUNNING);
        this.totalTicks = compound.getLong(NBT_TOTAL_TICKS);
        this.powered = compound.getBoolean(NBT_POWERED);

        ListTag listTag = compound.getList(NBT_SCHEDULES, Tag.TAG_COMPOUND);
        schedules.clear();
        for (int i = 0; i < listTag.size(); i++) {
            TrafficLightSchedule data = new TrafficLightSchedule();
            data.fromNbt(listTag.getCompound(i));
            schedules.add(data);
        }

        ListTag pendingList = compound.getList(NBT_PENDING_SCHEDULES, Tag.TAG_COMPOUND);
        pendingSchedules.clear();
        for (int i = 0; i < pendingList.size(); i++) {
            TrafficLightSchedule data = new TrafficLightSchedule();
            data.fromNbt(pendingList.getCompound(i));
            pendingSchedules.add(data);
        }

        ListTag trafficLightsList = compound.getList(NBT_TRAFFIC_LIGHT_LOCATIONS, Tag.TAG_COMPOUND);
        trafficLightLocations.clear();
        for (int i = 0; i < trafficLightsList.size(); i++) {
            WorldLocation loc = WorldLocation.loadFromNbt(trafficLightsList.getCompound(i));
            trafficLightLocations.add(loc);
        }

    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider)
    {       
        ListTag listTag = new ListTag();
        for (TrafficLightSchedule data : schedules) {
            listTag.add(data.toNbt());
        }

        ListTag pendingList = new ListTag();
        for (TrafficLightSchedule data : pendingSchedules) {
            pendingList.add(data.toNbt());
        }

        ListTag trafficLightsList = new ListTag();
        for (WorldLocation loc : trafficLightLocations) {
            trafficLightsList.add(loc.toNbt());
        }

        tag.putInt(NBT_TICKS, ticks);
        tag.putLong(NBT_TOTAL_TICKS, totalTicks);
        tag.putBoolean(NBT_POWERED, powered);
        tag.putBoolean(NBT_RUNNING, running);
        tag.put(NBT_SCHEDULES, listTag);
        tag.put(NBT_PENDING_SCHEDULES, pendingList);
        //tag.put("modes", modesTag);
        tag.put(NBT_TRAFFIC_LIGHT_LOCATIONS, trafficLightsList);
        super.saveAdditional(tag, provider);
    }

    private void instanceTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }

        // A chunk may be loaded before its block entity is available after joining a world.
        // Only unlink when the actual block is gone, not while its entity is still loading.
        boolean removedInvalidLinks = trafficLightLocations.removeIf(a -> {
            BlockPos linkedPos = a.getLocationBlockPos();
            if (!level.isLoaded(linkedPos)) return false;
            var linkedBlock = level.getBlockState(linkedPos).getBlock();
            return !(linkedBlock instanceof TrafficLightBlock) && !(linkedBlock instanceof LedDeviceBlock);
        });
        if (removedInvalidLinks) setChanged();
        trafficLightLocations.forEach(a -> {
            if (level.isLoaded(a.getLocationBlockPos())
                    && level.getBlockEntity(a.getLocationBlockPos()) instanceof LedDeviceBlockEntity led) {
                led.setControllerEnabled(running);
            }
        });

        TrafficLightSchedule activeSchedule = getFirstOrMainSchedule();
        int activeCycle = activeSchedule.isSequentialGreens()
            ? activeSchedule.getSequentialCycleTicks() : activeSchedule.getLongestCycleTicks();
        boolean cycleBoundary = running && totalTicks > 0
            && (activeSchedule.hasPhaseTimings()
                ? activeCycle > 0 && Math.floorMod(ticks, activeCycle) == 0
                : ticks == 0);
        if (cycleBoundary) {
            if (!pendingSchedules.isEmpty()) {
                activatePendingSchedules();
                ticks = 0;
                totalTicks = 0;
                notifyUpdate();
            }
            for (WorldLocation location : trafficLightLocations) {
                if (level.isLoaded(location.getLocationBlockPos())
                        && level.getBlockEntity(location.getLocationBlockPos()) instanceof TrafficLightBlockEntity light
                        && light.getControlType() == TrafficLightControlType.REMOTE) {
                    light.applyPendingPhaseId();
                }
            }
        }

        if (running && getFirstOrMainSchedule().hasPhaseTimings()) {
            TrafficLightSchedule schedule = getFirstOrMainSchedule();

            Map<PhaseKey, TrafficLightSchedule.PhaseState> phaseById = new HashMap<>();
            for (WorldLocation location : trafficLightLocations) {
                if (!level.isLoaded(location.getLocationBlockPos())) continue;
                if (level.getBlockEntity(location.getLocationBlockPos()) instanceof TrafficLightBlockEntity light
                    && light.getControlType() == TrafficLightControlType.REMOTE) {
                    int id = light.getPhaseId();
                    boolean crossing = light.getIcon() == TrafficLightIcon.PEDESTRIAN
                        || light.getIcon() == TrafficLightIcon.BIKE;
                    if (crossing && schedule.isSequentialGreens() && !light.getAdditionalPedestrianStopIds().isEmpty()) {
                        java.util.Set<Integer> stopIds = new java.util.LinkedHashSet<>();
                        stopIds.add(id);
                        stopIds.addAll(light.getAdditionalPedestrianStopIds());
                        TrafficLightSchedule.PhaseState combined = schedule.getSequentialCrossingPhaseAt(stopIds, ticks);
                        if (combined != null) {
                            light.applyTimedPhase(combined.colors(), combined.durationTicks(), combined.remainingTicks(),
                                combined.remainingTicks() > 0, false);
                        } else light.clearScheduledPhase();
                        continue;
                    }
                    PhaseKey key = new PhaseKey(id, crossing);
                    if (!phaseById.containsKey(key)) phaseById.put(key, schedule.isSequentialGreens()
                        ? schedule.getSequentialPhaseAt(id, crossing, ticks) : schedule.getPhaseAt(id, ticks));
                    TrafficLightSchedule.PhaseState phase = phaseById.get(key);
                    if (phase != null) {
                        light.applyTimedPhase(phase.colors(), phase.durationTicks(), phase.remainingTicks(),
                            phase.remainingTicks() > 0, schedule.isBlinkAtEnd());
                    } else light.clearScheduledPhase();
                }
            }
            ticks++;
            totalTicks++;
            // The phase cursor must be written with the chunk on world exit/reload.
            // setChanged() does not broadcast a block-entity packet every tick.
            setChanged();
            if (!schedule.isLoop() && ticks >= schedule.getLongestCycleTicks()) setRunning(false);
        } else if (!running) {
            for (WorldLocation location : trafficLightLocations) {
                if (level.isLoaded(location.getLocationBlockPos())
                    && level.getBlockEntity(location.getLocationBlockPos()) instanceof TrafficLightBlockEntity light
                    && light.getControlType() == TrafficLightControlType.REMOTE) light.pauseTimedPhase();
            }
        } else {
            TrafficLightSchedule schedule = this.getFirstOrMainSchedule();
            for (WorldLocation location : trafficLightLocations) {
                if (level.isLoaded(location.getLocationBlockPos())
                    && level.getBlockEntity(location.getLocationBlockPos()) instanceof TrafficLightBlockEntity light
                    && light.getControlType() == TrafficLightControlType.REMOTE) light.clearScheduleTiming();
            }
            List<TrafficLightScheduleEntryData> stateData = schedule.shouldChange(ticks);

            if (stateData == null) {
                ticks = 0;
                setChanged();
                if (!schedule.isLoop()) {
                    setRunning(false);
                }
                return;
            } else if (stateData.size() > 0) {
                for (TrafficLightScheduleEntryData entry : stateData) {
                    Collection<TrafficLightColor> colors = entry.getEnabledColors();
                    int phaseId = entry.getPhaseId();

                    trafficLightLocations.stream().filter(x -> 
                        level.getBlockEntity(x.getLocationBlockPos()) instanceof TrafficLightBlockEntity blockEntity &&
                        blockEntity.getControlType() == TrafficLightControlType.REMOTE &&
                        blockEntity.getPhaseId() == phaseId).forEach(a -> {
                        ((TrafficLightBlockEntity)level.getBlockEntity(a.getLocationBlockPos())).enableOnlyColors(colors);
                    });
                }                    
            }
            ticks++;
            totalTicks++;
            setChanged();
        }

        if (isPowered() && !level.hasNeighborSignal(pos)) {                
            this.setPowered(false);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TrafficLightControllerBlockEntity blockEntity) {
        blockEntity.instanceTick(level, pos, state);
    }


    /* GETTERS AND SETTERS */
   
    public List<TrafficLightSchedule> getSchedules() {
        return this.schedules;
    }

    public TrafficLightSchedule getFirstOrMainSchedule() {
        if (this.schedules.size() > 0) {
            return this.schedules.get(0);
        }

        return new TrafficLightSchedule();
    }

    public TrafficLightSchedule getScheduleForEditing() {
        return pendingSchedules.isEmpty() ? getFirstOrMainSchedule() : pendingSchedules.get(0);
    }

    public void setFirstOrMainSchedule(TrafficLightSchedule schedule) {
        pendingSchedules.clear();
        if (this.schedules.size() > 0)
            this.schedules.remove(0);

        this.schedules.add(0, schedule);
        this.ticks = 0;
        this.totalTicks = 0;
        notifyUpdate();
    }

    private void activatePendingSchedules() {
        if (pendingSchedules.isEmpty()) return;
        schedules.clear();
        schedules.addAll(pendingSchedules);
        pendingSchedules.clear();
    }

    public void setSchedules(List<TrafficLightSchedule> schedules) {
        List<TrafficLightSchedule> updated = new ArrayList<>(schedules);
        if (sameSchedules(this.schedules, updated)) {
            if (!pendingSchedules.isEmpty()) {
                pendingSchedules.clear();
                notifyUpdate();
            }
            return;
        }
        if (sameSchedules(pendingSchedules, updated)) return;

        TrafficLightSchedule active = getFirstOrMainSchedule();
        int cycle = active.isSequentialGreens() ? active.getSequentialCycleTicks()
            : active.hasPhaseTimings() ? active.getLongestCycleTicks() : active.getTotalDurationTicks();
        if (running && cycle > 0) {
            pendingSchedules.clear();
            pendingSchedules.addAll(updated);
            notifyUpdate();
            return;
        }
        pendingSchedules.clear();
        this.schedules.clear();
        this.schedules.addAll(updated);
        ticks = 0;
        totalTicks = 0;
        notifyUpdate();
    }

    private static boolean sameSchedules(List<TrafficLightSchedule> left, List<TrafficLightSchedule> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).toNbt().equals(right.get(i).toNbt())) return false;
        }
        return true;
    }

    public int getCurrentTick() {
        return ticks;
    }

    public void setCurrentTick(int t) {
        this.ticks = t;
        notifyUpdate();
    }

    public boolean isRunning() {
        return this.running;
    }

    public void setRunning(boolean b) {
        this.running = b;
        if (!b) {
            if (!pendingSchedules.isEmpty()) {
                activatePendingSchedules();
                ticks = 0;
                totalTicks = 0;
            }
            if (level != null) {
                for (WorldLocation location : trafficLightLocations) {
                    if (level.isLoaded(location.getLocationBlockPos())
                            && level.getBlockEntity(location.getLocationBlockPos()) instanceof TrafficLightBlockEntity light) {
                        light.applyPendingPhaseId();
                    }
                }
            }
        }
        notifyUpdate();
    }

    public void startSchedule(boolean forceRestart) {
        if (forceRestart || !this.isFirstIteration()) {
            activatePendingSchedules();
            this.totalTicks = 0;
            this.ticks = 0;
            this.running = true;
            notifyUpdate();
        }
    }

    public void stopSchedule() {
        this.running = false;
        this.totalTicks = 0;
        this.ticks = 0;
        notifyUpdate();
    }

    public boolean isFirstIteration() {
        return this.totalTicks == this.ticks;
    }

    public void setPowered(boolean b) {
        this.powered = b;
        notifyUpdate();
    }

    public boolean isPowered() {
        return this.powered;
    }

    public List<WorldLocation> getTrafficLightLocations() {
        return trafficLightLocations;
    }

    public void addTrafficLightLocation(WorldLocation loc) {
        if (!trafficLightLocations.contains(loc)) {
            trafficLightLocations.add(loc);
            notifyUpdate();
        }
    }

    public void removeTrafficLightLocation(WorldLocation loc) {
        trafficLightLocations.removeIf(x -> x.equals(loc));
        notifyUpdate();
    }
}
