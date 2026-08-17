package com.destan.trafficengine.client.widgets.trafficlight;

import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.mrjulsen.mcdragonlib.annotations.SupportsEvents;
import de.mrjulsen.mcdragonlib.events.EventListenerWrapper;
import de.mrjulsen.mcdragonlib.events.IEvent;
import de.mrjulsen.mcdragonlib.events.IEventDispatcher;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@SupportsEvents({
    TrafficLightConfig.UpdateEvent.class
})
public class TrafficLightConfig implements IEventDispatcher<TrafficLightConfig> {

    public record UpdateEvent() implements IEvent {}

    private final Map<Class<? extends IEvent>, PriorityQueue<EventListenerWrapper<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public Map<Class<? extends IEvent>, PriorityQueue<EventListenerWrapper<?>>> getEventListeners() {
        return listeners;
    }
    
    public final Level level;
    public final BlockPos blockPos;

    public final Set<TrafficLightColor> enabledColors = new HashSet<>();
    public TrafficLightType type = TrafficLightType.NOCOUNTDOWN;
    public TrafficLightModel model = TrafficLightModel.THREE_LIGHTS;
    public TrafficLightIcon icon = TrafficLightIcon.NONE;
    public TrafficLightControlType controlType = TrafficLightControlType.STATIC;
    public TrafficLightColor[] colors = new TrafficLightColor[TrafficLightModel.maxRequiredSlots()];
    public int phaseId = 0;
    public boolean scheduleEnabled = true;

    public TrafficLightConfig(Level level, BlockPos pos) {
        this.level = level;
        this.blockPos = pos;

        if (level.getBlockState(pos).getBlock() instanceof TrafficLightBlock) {            
            this.model = level.getBlockState(pos).getValue(TrafficLightBlock.MODEL);
        }
        if (level.getBlockEntity(pos) instanceof TrafficLightBlockEntity blockEntity) {
            for (TrafficLightColor color : blockEntity.getEnabledColors()) {
                this.enabledColors.add(color);
            }
            this.type = blockEntity.getTLType();
            this.icon = blockEntity.getIcon();
            this.controlType = blockEntity.getControlType();
            TrafficLightColor[] slots = blockEntity.getColorSlots();
            for (int i = 0; i < slots.length && i < this.colors.length; i++) {
                this.colors[i] = slots[i];
            }
            this.phaseId = blockEntity.getPhaseId();
            this.scheduleEnabled = blockEntity.isRunning();
        }
    }

    public void notifyUpdate() {
        invokeEvent(this, new UpdateEvent());
    }
}
