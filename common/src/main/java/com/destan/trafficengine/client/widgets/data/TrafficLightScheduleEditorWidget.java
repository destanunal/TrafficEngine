package com.destan.trafficengine.client.widgets.data;

import java.util.Map;

import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLScrollBar;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLScrollBar.Orientation;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.BorderLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.Direction;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.EAlign;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.RenderLayer;
import de.mrjulsen.mcdragonlib.client.render.DLTextureSheet;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.DLUtils;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.client.widgets.TrafficLightScheduleEntry;
import com.destan.trafficengine.client.widgets.TrafficLightSequentialEntry;
import com.destan.trafficengine.data.TrafficLightSchedule;
import com.destan.trafficengine.data.TrafficLightScheduleEntryData;

public class TrafficLightScheduleEditorWidget extends DLGuiComponent {
    
    public static final DLTextureSheet ICONS = new DLTextureSheet(DLUtils.resourceLocation(TrafficEngine.MOD_ID, "textures/gui/traffic_light_schedule_icons.png"));
    public static final String SPRITE_TIMELINE_NODE = "timeline_node";
    public static final String SPRITE_TIMELINE_ACTION = "timeline_action";
    public static final String SPRITE_TIMELINE_DELAY = "timeline_delay";
    public static final String SPRITE_TIMELINE_EDGE = "timeline_edge";    

    public static final int SPRITE_SIZE = 9;
    public static final int PADDING = 8;
    public static final int ENTRY_PADDING_LEFT = 6;
    public static final int ENTRY_PADDING_RIGHT = 8;
    public static final int ENTRY_PADDING_TIMELINE_TEXT = 6;
    public static final int TIMELINE_ICON_X = PADDING + ENTRY_PADDING_LEFT;
    public static final int TEXT_X = PADDING + ENTRY_PADDING_LEFT + SPRITE_SIZE + ENTRY_PADDING_TIMELINE_TEXT;
    
    private final DLScrollBar scrollBar;
    private final DLPanel contentPanel;

    private final TrafficLightSchedule schedule;
    private final boolean showIdBox;
    private final Map<Integer, TrafficLightType> signalTypes;

    public TrafficLightScheduleEditorWidget(int x, int y, int w, int h, TrafficLightSchedule schedule, boolean showIdBox, Map<Integer, TrafficLightType> signalTypes) {
        super(x, y, w, h);
        this.schedule = schedule;
        this.showIdBox = showIdBox;
        this.signalTypes = signalTypes;

        scrollBar = addComponent(new DLScrollBar(0, 0, 8, 0, Orientation.VERTICAL));
        scrollBar.layoutContraint.set(BorderLayout.BorderPosition.EAST);
        scrollBar.scrollerSize.set(0);
        scrollBar.inputConsumptionPolicy.set(c -> true);

        DLPanel contentPanelWrapper = addComponent(new DLPanel(0, 0, 10, 10));
        contentPanelWrapper.layoutContraint.set(BorderLayout.BorderPosition.CENTER);
        contentPanelWrapper.addEventListener(DLGuiStandardEvents.RenderEvent.class, (s, e) -> {
            if (e.layer() == RenderLayer.MAIN) {
                ICONS.getSprite("container").render(e.graphics(), 0, 0, s.width(), s.height());
            }
            return false;
        });
        // DragonLib does not clip its FRONT layer, where number-picker text is drawn.
        contentPanelWrapper.addEventListener(DLGuiStandardEvents.RenderPreEvent.class, (s, e) -> {
            if (e.layer() == RenderLayer.FRONT) GuiUtils.enableScissor(e.graphics(), e.renderBounds());
            return false;
        });
        contentPanelWrapper.addEventListener(DLGuiStandardEvents.RenderPostEvent.class, (s, e) -> {
            if (e.layer() == RenderLayer.FRONT) GuiUtils.disableScissor(e.graphics());
            return false;
        });

        contentPanel = contentPanelWrapper.addComponent(new DLPanel(1, 1, contentPanelWrapper.width() - 2, contentPanelWrapper.height() - 2));
        contentPanel.inputConsumptionPolicy.set(c -> c != ConsumptionType.SCROLL);
        contentPanel.anchor.set(EAlign.values());
        contentPanelWrapper.addEventListener(DLGuiStandardEvents.ScrollEvent.class, scrollBar::invokeEvent);

        FlowLayout contentLayout = new FlowLayout();
        contentLayout.fillCrossAxis.set(true);
        contentLayout.flowDirection.set(Direction.VERTICAL);
        contentLayout.wrap.set(false);
        contentLayout.padding.set(new Padding(PADDING / 2, 0, PADDING / 2, 0));
        contentPanel.addEventListener(DLGuiStandardEvents.ComponentLayoutUpdatedEvent.class, (s, e) -> {
            scrollBar.screenSize.set(s.height());
            scrollBar.max.set(e.layoutResult().contentHeight());
            return false;
        });
        contentPanel.layout.set(contentLayout);
        scrollBar.addEventListener(DLScrollBar.ValueChangedEvent.class, (s, e) -> {
            contentPanel.setScrollOffsetY(e.value());
            return false;
        });
        refresh();
        
        BorderLayout layout = new BorderLayout(0, 0);
        this.layout.set(layout);

    }

    public void refresh() {
        double scrollValue = scrollBar.value.get();
        contentPanel.clearComponents();
        for (TrafficLightScheduleEntryData entry : schedule.getEntries()) {
            final TrafficLightScheduleEntryData dataEntry = entry;
            java.util.function.Consumer<TrafficLightScheduleEntryData> remove = (data) -> {
                schedule.getEntries().removeIf(a -> a == dataEntry);
                refresh();
            };
            java.util.function.BiConsumer<TrafficLightScheduleEntryData, Integer> reorder = (e, offset) -> {
                int len = schedule.getEntries().size();
                int idx = schedule.getEntries().indexOf(dataEntry);
                if (idx < 0) {
                    return;
                }
                if (offset > 0 && idx < len - 1) {
                    schedule.getEntries().moveForth(idx, 1);
                } else if (offset < 0 && idx > 0) {
                    schedule.getEntries().moveBack(idx, 1);
                }
                refresh();
            };
            if (showIdBox && schedule.isSequentialGreens()) {
                contentPanel.addComponent(new TrafficLightSequentialEntry(0, 0, width() - 10,
                    schedule, entry, remove, reorder));
            } else {
                contentPanel.addComponent(new TrafficLightScheduleEntry(0, 0, width() - 10,
                    entry, !showIdBox, signalTypes, remove, reorder));
            }
        }
        scrollBar.value.set(scrollValue);
    }



}
