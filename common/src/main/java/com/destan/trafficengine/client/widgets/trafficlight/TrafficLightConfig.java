package com.destan.trafficengine.client.widgets.trafficlight;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.mrjulsen.mcdragonlib.annotations.SupportsEvents;
import de.mrjulsen.mcdragonlib.events.EventListenerWrapper;
import de.mrjulsen.mcdragonlib.events.IEvent;
import de.mrjulsen.mcdragonlib.events.IEventDispatcher;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightControlType;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.network.packets.cts.TrafficLightPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
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
    public final Set<Integer> additionalPedestrianStopIds = new HashSet<>();
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
            this.phaseId = blockEntity.getPhaseIdForEditing();
            this.additionalPedestrianStopIds.addAll(blockEntity.getAdditionalPedestrianStopIds());
            this.scheduleEnabled = blockEntity.isRunning();
        }

        // Existing two-light signals used to retain the second (yellow) slot
        // from the default three-light layout. Correct that legacy default.
        if (model == TrafficLightModel.TWO_LIGHTS
                && colors[0] == TrafficLightColor.RED && colors[1] == TrafficLightColor.YELLOW) {
            colors[1] = TrafficLightColor.GREEN;
        }
    }

    public void useTwoLightColors() {
        if (model == TrafficLightModel.TWO_LIGHTS) {
            colors[0] = TrafficLightColor.RED;
            colors[1] = TrafficLightColor.GREEN;
        }
    }

    public void notifyUpdate() {
        invokeEvent(this, new UpdateEvent());
        sendToServer();
    }

    public void sendToServer() {
        int maxSlots = TrafficLightModel.maxRequiredSlots();
        TrafficLightColor[] safeColors = new TrafficLightColor[maxSlots];
        for (int i = 0; i < maxSlots; i++) {
            safeColors[i] = (i < colors.length && colors[i] != null) ? colors[i] : TrafficLightColor.NONE;
        }
        ModNetworkManager.UPDATE_TRAFFIC_LIGHT_PACKET.send(NetworkDirection.toServer(), new TrafficLightPacket(
            blockPos, List.copyOf(enabledColors), type, model, icon, controlType,
            safeColors, phaseId, List.copyOf(additionalPedestrianStopIds), scheduleEnabled
        ));
    }
}
