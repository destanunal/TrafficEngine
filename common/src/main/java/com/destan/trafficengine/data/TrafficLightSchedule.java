package com.destan.trafficengine.data;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.destan.trafficengine.data.NbtSerializable;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightTrigger;
import com.destan.trafficengine.util.OrderedArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public class TrafficLightSchedule implements NbtSerializable {

    private static final String NBT_LOOP = "loop";
    private static final String NBT_ENTRIES = "entries";
    private static final String NBT_TRIGGER = "trigger";
    private static final String NBT_PHASE_TIMINGS = "phaseTimings";
    private static final String NBT_BLINK_AT_END = "blinkAtEnd";
    private static final String NBT_SEQUENTIAL_GREENS = "sequentialGreens";
    private static final String NBT_MANUAL_CONTROLLER = "manualController";
    private static final String NBT_RED_YELLOW_TICKS = "redYellowTicks";
    private static final String NBT_RED_YELLOW_BEFORE_GREEN = "redYellowBeforeGreen";
    public static final int SAFETY_INTERVAL_SECONDS = 2;
    public static final int SAFETY_INTERVAL_TICKS = SAFETY_INTERVAL_SECONDS * 20;
    private static final int CROSSING_INTERVAL_TICKS = (SAFETY_INTERVAL_SECONDS + 1) * 20;
    private static final int PEDESTRIAN_RED_BEFORE_VEHICLE_TRANSITION_TICKS = 40;
    public static final int MAX_RED_YELLOW_SECONDS = 10;
    public static final int MIN_PHASE_ID = 1;
    public static final int MAX_PHASE_ID = 100;

    public static boolean isValidPhaseId(int id) {
        return id >= MIN_PHASE_ID && id <= MAX_PHASE_ID;
    }
    
    private OrderedArrayList<TrafficLightScheduleEntryData> entries = new OrderedArrayList<>();
    private boolean loop = true;
    private TrafficLightTrigger trigger = TrafficLightTrigger.NONE;
    private boolean phaseTimings = true;
    private boolean blinkAtEnd = false;
    private boolean sequentialGreens = false;
    private boolean manualController = false;
    private int redYellowTicks = 40;
    private boolean redYellowBeforeGreen = true;

    public record PhaseState(Collection<TrafficLightColor> colors, int durationTicks, int remainingTicks) {}
    private record TimedEntry(int at, TrafficLightScheduleEntryData entry) {}

    public TrafficLightSchedule copy() {
        TrafficLightSchedule schedule = new TrafficLightSchedule();
        schedule.entries.addAll(entries.stream().map(x -> x.copy()).toList());
        schedule.loop = loop;
        schedule.trigger = trigger;
        schedule.phaseTimings = phaseTimings;
        schedule.blinkAtEnd = blinkAtEnd;
        schedule.sequentialGreens = sequentialGreens;
        schedule.manualController = manualController;
        schedule.redYellowTicks = redYellowTicks;
        schedule.redYellowBeforeGreen = redYellowBeforeGreen;
        return schedule;
    }
    
    public OrderedArrayList<TrafficLightScheduleEntryData> getEntries() {
        return this.entries;
    }

    public boolean isLoop() {
        return true;
    }

    public TrafficLightTrigger getTrigger() {
        return this.trigger;
    }

    public void setLoop(boolean b)  {
        this.loop = true;
    }

    public void setTrigger(TrafficLightTrigger trigger) {
        this.trigger = trigger;
    }

    public boolean hasPhaseTimings() {
        return phaseTimings;
    }

    public boolean isBlinkAtEnd() {
        return blinkAtEnd;
    }

    public void setBlinkAtEnd(boolean blinkAtEnd) {
        this.blinkAtEnd = blinkAtEnd;
    }

    public boolean isSequentialGreens() {
        return sequentialGreens;
    }

    public boolean isManualController() {
        return manualController;
    }

    public void setManualController(boolean manualController) {
        if (manualController && !this.manualController) {
            int cycle = getSequentialCycleTicks();
            for (TrafficLightScheduleEntryData entry : entries) {
                if (entry.getDurationTicks() > 0 && entry.getManualRedTicks() == 0) {
                    entry.setManualRedTicks(Math.max(0, cycle - entry.getDurationTicks() - redYellowTicks * 2));
                }
            }
        }
        this.manualController = manualController;
    }

    public int getRedYellowTicks() {
        return redYellowTicks;
    }

    public int getRedYellowSeconds() {
        return redYellowTicks / 20;
    }

    public void setRedYellowSeconds(int seconds) {
        redYellowTicks = Math.max(0, Math.min(MAX_RED_YELLOW_SECONDS, seconds)) * 20;
    }

    public boolean isRedYellowBeforeGreen() {
        return redYellowBeforeGreen;
    }

    public void setRedYellowBeforeGreen(boolean redYellowBeforeGreen) {
        this.redYellowBeforeGreen = redYellowBeforeGreen;
    }

    /** Manual red covers the pure-red interval; the two yellow transitions are additional. */
    public int getSequentialCycleTicks() {
        int minimumCycle = 0;
        TrafficLightScheduleEntryData first = null;
        for (TrafficLightScheduleEntryData entry : entries) {
            int duration = entry.getDurationTicks();
            if (duration > 0) {
                if (first == null) first = entry;
                minimumCycle += duration + SAFETY_INTERVAL_TICKS + redYellowTicks * 2;
            }
        }
        if (manualController && first != null) {
            // Keep all directions on one cycle. Invalid external data cannot create overlapping greens.
            return Math.max(minimumCycle, first.getDurationTicks() + first.getManualRedTicks() + redYellowTicks * 2);
        }
        return minimumCycle;
    }

    /** Converts an existing controller plan into one green interval per ID for the simple editor. */
    public void convertToSequentialGreens() {
        if (sequentialGreens) return;
        Map<Integer, Integer> greenTicks = new LinkedHashMap<>();
        for (TrafficLightScheduleEntryData entry : entries) {
            if (entry.getEnabledColors().stream().anyMatch(c -> c.isSimilar(TrafficLightColor.GREEN))) {
                greenTicks.merge(entry.getPhaseId(), entry.getDurationTicks(), Integer::sum);
            }
        }
        entries.clear();
        greenTicks.forEach((id, ticks) -> {
            TrafficLightScheduleEntryData entry = new TrafficLightScheduleEntryData();
            entry.setPhaseId(id);
            entry.setDurationTicks(ticks);
            entry.enableOnlyColors(List.of(TrafficLightColor.GREEN));
            entries.add(entry);
        });
        sequentialGreens = true;
        phaseTimings = true;
    }

    public PhaseState getSequentialPhaseAt(int id, boolean pedestrianOrBike, long elapsedTicks) {
        int cycle = getSequentialCycleTicks();
        int cursor = 0;
        int ownStart = -1;
        int ownGreen = 0;
        for (TrafficLightScheduleEntryData entry : entries) {
            int duration = entry.getDurationTicks();
            if (duration <= 0) continue;
            if (entry.getPhaseId() == id && ownStart < 0) {
                ownStart = cursor;
                ownGreen = duration;
            }
            // Green, outgoing yellow, all-red safety, then incoming yellow.
            cursor += duration + SAFETY_INTERVAL_TICKS + redYellowTicks * 2;
        }
        if (ownStart < 0 || cycle == 0) return null;
        // Start at the all-red safety interval before the first direction.
        int sinceGreenStarted = Math.floorMod(elapsedTicks - SAFETY_INTERVAL_TICKS - redYellowTicks - ownStart, cycle);
        int yellowEnd = ownGreen + redYellowTicks;
        int incomingYellowStart = cycle - redYellowTicks;

        if (!pedestrianOrBike) {
            if (sinceGreenStarted < ownGreen) {
                return new PhaseState(List.of(TrafficLightColor.GREEN), ownGreen, ownGreen - sinceGreenStarted);
            }
            if (sinceGreenStarted < yellowEnd) {
                return new PhaseState(List.of(TrafficLightColor.YELLOW), redYellowTicks, yellowEnd - sinceGreenStarted);
            }
            if (sinceGreenStarted < incomingYellowStart) {
                return new PhaseState(List.of(TrafficLightColor.RED), incomingYellowStart - yellowEnd,
                    incomingYellowStart - sinceGreenStarted);
            }
            return new PhaseState(redYellowBeforeGreen
                ? List.of(TrafficLightColor.RED, TrafficLightColor.YELLOW) : List.of(TrafficLightColor.YELLOW),
                redYellowTicks, cycle - sinceGreenStarted);
        }

        // Keep the existing opening delay. Give pedestrians two seconds of red
        // before the matching vehicle signal starts its pre-green yellow phase.
        int crossingGreenStart = yellowEnd + CROSSING_INTERVAL_TICKS;
        int crossingGreenEnd = incomingYellowStart - PEDESTRIAN_RED_BEFORE_VEHICLE_TRANSITION_TICKS;
        int pedestrianGreen = crossingGreenEnd - crossingGreenStart;
        if (pedestrianGreen <= 0) {
            return new PhaseState(List.of(TrafficLightColor.RED), cycle, cycle - sinceGreenStarted);
        }
        int pedestrianRed = cycle - pedestrianGreen;
        boolean green = sinceGreenStarted >= crossingGreenStart && sinceGreenStarted < crossingGreenEnd;
        int remaining = green ? crossingGreenEnd - sinceGreenStarted
            : sinceGreenStarted >= crossingGreenEnd
                ? cycle - sinceGreenStarted + crossingGreenStart
                : crossingGreenStart - sinceGreenStarted;
        return new PhaseState(List.of(green ? TrafficLightColor.GREEN : TrafficLightColor.RED),
            green ? pedestrianGreen : pedestrianRed, remaining);
    }

    /** A crossing stays red while any selected vehicle direction requires it to stop. */
    public PhaseState getSequentialCrossingPhaseAt(Collection<Integer> vehicleIds, long elapsedTicks) {
        if (vehicleIds.isEmpty()) return null;
        if (vehicleIds.size() == 1) return getSequentialPhaseAt(vehicleIds.iterator().next(), true, elapsedTicks);
        int cycle = getSequentialCycleTicks();
        if (cycle <= 0) return null;

        PhaseState first = combinedCrossingState(vehicleIds, elapsedTicks, cycle);
        if (first == null) return new PhaseState(List.of(TrafficLightColor.RED), cycle, cycle);
        boolean initiallyGreen = first.colors().contains(TrafficLightColor.GREEN);
        int elapsed = 0;
        while (elapsed < cycle) {
            PhaseState current = combinedCrossingState(vehicleIds, elapsedTicks + elapsed, cycle);
            if (current == null) return new PhaseState(List.of(TrafficLightColor.RED), cycle, cycle - elapsed);
            elapsed += Math.max(1, current.remainingTicks());
            if (elapsed >= cycle) break;
            PhaseState next = combinedCrossingState(vehicleIds, elapsedTicks + elapsed, cycle);
            if (next == null || next.colors().contains(TrafficLightColor.GREEN) != initiallyGreen) break;
        }
        int remaining = Math.min(elapsed, cycle);
        return new PhaseState(List.of(initiallyGreen ? TrafficLightColor.GREEN : TrafficLightColor.RED),
            remaining, remaining);
    }

    private PhaseState combinedCrossingState(Collection<Integer> vehicleIds, long elapsedTicks, int cycle) {
        boolean allSafe = true;
        int nextIndividualChange = cycle;
        for (int id : vehicleIds) {
            PhaseState state = getSequentialPhaseAt(id, true, elapsedTicks);
            if (state == null) return null;
            allSafe &= state.colors().contains(TrafficLightColor.GREEN);
            nextIndividualChange = Math.min(nextIndividualChange, Math.max(1, state.remainingTicks()));
        }
        return new PhaseState(List.of(allSafe ? TrafficLightColor.GREEN : TrafficLightColor.RED),
            nextIndividualChange, nextIndividualChange);
    }

    /** Older schedules stored a delay before each color change. Convert them to color durations. */
    public void convertToPhaseTimings(boolean ownSchedule) {
        if (phaseTimings) return;
        int cycle = getTotalDurationTicks();
        Map<Integer, List<TimedEntry>> byId = new LinkedHashMap<>();
        int at = 0;
        for (TrafficLightScheduleEntryData entry : entries) {
            at += entry.getDurationTicks();
            int id = ownSchedule ? 0 : entry.getPhaseId();
            byId.computeIfAbsent(id, unused -> new ArrayList<>()).add(new TimedEntry(at, entry));
        }
        OrderedArrayList<TrafficLightScheduleEntryData> converted = new OrderedArrayList<>();
        if (cycle > 0) {
            for (Map.Entry<Integer, List<TimedEntry>> group : byId.entrySet()) {
                List<TimedEntry> events = group.getValue();
                int firstAt = Math.min(events.get(0).at(), cycle);
                if (firstAt > 0) addConverted(converted, group.getKey(), events.get(events.size() - 1).entry(), firstAt);
                for (int i = 0; i < events.size(); i++) {
                    int next = i + 1 < events.size() ? events.get(i + 1).at() : cycle;
                    int duration = Math.max(0, Math.min(next, cycle) - events.get(i).at());
                    if (duration > 0) addConverted(converted, group.getKey(), events.get(i).entry(), duration);
                }
            }
        }
        entries = converted;
        phaseTimings = true;
    }

    private static void addConverted(OrderedArrayList<TrafficLightScheduleEntryData> target, int id, TrafficLightScheduleEntryData source, int ticks) {
        TrafficLightScheduleEntryData entry = source.copy();
        entry.setPhaseId(id);
        entry.setDurationTicks(ticks);
        entry.setBlinkSeconds(0);
        target.add(entry);
    }

    public PhaseState getPhaseAt(int id, long elapsedTicks) {
        return getPhaseAt(id, elapsedTicks, false);
    }

    public PhaseState getOwnPhaseAt(long elapsedTicks) {
        return getPhaseAt(0, elapsedTicks, true);
    }

    private PhaseState getPhaseAt(int id, long elapsedTicks, boolean allIds) {
        long cycle = 0;
        TrafficLightScheduleEntryData last = null;
        for (TrafficLightScheduleEntryData entry : entries) {
            if ((allIds || entry.getPhaseId() == id) && entry.getDurationTicks() > 0) {
                cycle += entry.getDurationTicks();
                last = entry;
            }
        }
        if (cycle == 0 || last == null) return null;
        long position = Math.floorMod(elapsedTicks, cycle);
        for (TrafficLightScheduleEntryData entry : entries) {
            if ((allIds || entry.getPhaseId() == id) && entry.getDurationTicks() > 0) {
                if (position < entry.getDurationTicks()) return new PhaseState(entry.getEnabledColors(), entry.getDurationTicks(), (int)(entry.getDurationTicks() - position));
                position -= entry.getDurationTicks();
            }
        }
        return new PhaseState(last.getEnabledColors(), last.getDurationTicks(), 0);
    }

    public int getLongestCycleTicks() {
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        for (TrafficLightScheduleEntryData entry : entries) totals.merge(entry.getPhaseId(), entry.getDurationTicks(), Integer::sum);
        return totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public int getTotalDurationTicks() {
        return entries.stream().mapToInt(x -> x.getDurationTicks()).sum();
    }

    /**
     * Check if there is something to change.
     * @param currentTick
     * @return List of phaseIDs. Returns {@code null} if {@code currentTick} is out of bounds. Returns an empty list, if thee is nothing to change. Returns a list containing the indices of states to change, if there is something to change.
     */
    public OrderedArrayList<TrafficLightScheduleEntryData> shouldChange(int currentTick) {
        OrderedArrayList<TrafficLightScheduleEntryData> changeEntries = new OrderedArrayList<>();
        int keyTime = 0;

        for (TrafficLightScheduleEntryData entry : entries) {
            keyTime += entry.getDurationTicks();
            if (keyTime == currentTick) {
                changeEntries.add(entry);
            }

            if (currentTick < keyTime) {
                return new OrderedArrayList<>(changeEntries.stream().distinct().toList());
            }
        }

        return changeEntries.size() <= 0 ? null : new OrderedArrayList<>(changeEntries.stream().distinct().toList());
    }
    
    public Collection<TrafficLightColor> getColorsForUpdate(int index) {
        if (index < 0 || index >= entries.size())
            return Collections.emptyList();

        return entries.get(index).getEnabledColors();
    }

    public int getPhaseId(int index) {
        if (index < 0 || index >= entries.size())
            return Integer.MAX_VALUE;

        return entries.get(index).getPhaseId();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        
        ListTag listTag = new ListTag();
        for (TrafficLightScheduleEntryData data : entries) {
            listTag.add(data.toNbt());
        }

        tag.putBoolean(NBT_LOOP, true);
        tag.putBoolean(NBT_PHASE_TIMINGS, phaseTimings);
        tag.putBoolean(NBT_BLINK_AT_END, blinkAtEnd);
        tag.putBoolean(NBT_SEQUENTIAL_GREENS, sequentialGreens);
        tag.putBoolean(NBT_MANUAL_CONTROLLER, manualController);
        tag.putInt(NBT_RED_YELLOW_TICKS, redYellowTicks);
        tag.putBoolean(NBT_RED_YELLOW_BEFORE_GREEN, redYellowBeforeGreen);
        tag.putByte(NBT_TRIGGER, trigger.getIndex());
        tag.put(NBT_ENTRIES, listTag);
        return tag;
    }

    public void fromNbt(CompoundTag tag) {
        entries.clear();
        loop = true;
        phaseTimings = tag.getBoolean(NBT_PHASE_TIMINGS);
        sequentialGreens = tag.getBoolean(NBT_SEQUENTIAL_GREENS);
        manualController = tag.getBoolean(NBT_MANUAL_CONTROLLER);
        redYellowTicks = tag.contains(NBT_RED_YELLOW_TICKS) ? Math.max(0, Math.min(MAX_RED_YELLOW_SECONDS * 20, tag.getInt(NBT_RED_YELLOW_TICKS))) : 40;
        redYellowBeforeGreen = !tag.contains(NBT_RED_YELLOW_BEFORE_GREEN) || tag.getBoolean(NBT_RED_YELLOW_BEFORE_GREEN);
        trigger = TrafficLightTrigger.getTriggerByIndex(tag.getTagType(NBT_TRIGGER) == Tag.TAG_INT ? (byte)tag.getInt(NBT_TRIGGER) : tag.getByte(NBT_TRIGGER));
        ListTag listTag = tag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
        
        // START Backward compatibility
        double lastTime = -1;
        boolean migration = false;
        // END Backward compatibility

        for (int i = 0; i < listTag.size(); i++) {
            TrafficLightScheduleEntryData data = new TrafficLightScheduleEntryData();            
            data.fromNbt(listTag.getCompound(i));

            // START Backward compatibility
            if (migration = data.shouldMigrate(listTag.getCompound(i))) {                
                double lTime = data.getDurationSeconds();
                if (lastTime >= 0) {
                    data.setDurationSeconds(lastTime);
                }
                lastTime = lTime;
            }            
            // END Backward compatibility

            entries.add(data);
        }
        
        // START Backward compatibility
        if (migration && entries.size() > 0) {
            entries.get(0).setDurationSeconds(lastTime);
        }
        // END Backward compatibility

        blinkAtEnd = tag.contains(NBT_BLINK_AT_END) ? tag.getBoolean(NBT_BLINK_AT_END)
            : entries.stream().anyMatch(entry -> entry.getBlinkTicks() > 0);
        
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBoolean(phaseTimings);
        buf.writeBoolean(blinkAtEnd);
        buf.writeBoolean(sequentialGreens);
        buf.writeBoolean(manualController);
        buf.writeInt(redYellowTicks);
        buf.writeBoolean(redYellowBeforeGreen);
        buf.writeByte(trigger.getIndex());
        buf.writeInt(entries.size());
        for (TrafficLightScheduleEntryData data : entries) {
            data.toBytes(buf);
        }
    }

    public static TrafficLightSchedule fromBytes(FriendlyByteBuf buf) {
        TrafficLightSchedule schedule = new TrafficLightSchedule();
        schedule.setLoop(buf.readBoolean());
        schedule.phaseTimings = buf.readBoolean();
        schedule.blinkAtEnd = buf.readBoolean();
        schedule.sequentialGreens = buf.readBoolean();
        schedule.manualController = buf.readBoolean();
        schedule.redYellowTicks = Math.max(0, Math.min(MAX_RED_YELLOW_SECONDS * 20, buf.readInt()));
        schedule.redYellowBeforeGreen = buf.readBoolean();
        schedule.setTrigger(TrafficLightTrigger.getTriggerByIndex(buf.readByte()));
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            schedule.entries.add(TrafficLightScheduleEntryData.fromBytes(buf));
        }
        return schedule;
    }

    @Override
    public CompoundTag serializeNbt() {
        return toNbt();
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        fromNbt(nbt);
    }
}
