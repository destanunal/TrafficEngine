package com.destan.trafficengine.registry;

import de.mrjulsen.mcdragonlib.network.DLNetworkManager;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.network.NetworkPacketType;
import de.mrjulsen.mcdragonlib.util.DLUtils;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.network.packets.cts.ColorPaletteItemPacket;
import com.destan.trafficengine.network.packets.cts.CreateNewTrafficSignTexturePacket;
import com.destan.trafficengine.network.packets.cts.CreativePatternCataloguePacket;
import com.destan.trafficengine.network.packets.cts.GetTrafficSignTexturePacket;
import com.destan.trafficengine.network.packets.cts.LinkerModePacket;
import com.destan.trafficengine.network.packets.cts.LedDevicePacket;
import com.destan.trafficengine.network.packets.cts.PaintBrushPacket;
import com.destan.trafficengine.network.packets.cts.PatternCatalogueDeletePacket;
import com.destan.trafficengine.network.packets.cts.PatternCatalogueIndexPacket;
import com.destan.trafficengine.network.packets.cts.PatternCatalogueIndexPacketGui;
import com.destan.trafficengine.network.packets.cts.RoadBuilderBuildRoadPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderDataPacket;
import com.destan.trafficengine.network.packets.cts.RoadBuilderResetPacket;
import com.destan.trafficengine.network.packets.cts.StreetLampConfigPacket;
import com.destan.trafficengine.network.packets.cts.TownSignPacket;
import com.destan.trafficengine.network.packets.cts.TrafficLightControllerPacket;
import com.destan.trafficengine.network.packets.cts.TrafficLightPacket;
import com.destan.trafficengine.network.packets.cts.TrafficLightSchedulePacket;
import com.destan.trafficengine.network.packets.cts.TrafficSignPatternPacket;
import com.destan.trafficengine.network.packets.cts.WritableSignPacket;
import com.destan.trafficengine.network.packets.stc.TrafficSignTextureResetPacket;

public class ModNetworkManager {

    public static final DLNetworkManager NETWORK = new DLNetworkManager(DLUtils.resourceLocation(TrafficEngine.MOD_ID, "network"), "2");

    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, GetTrafficSignTexturePacket.Request, GetTrafficSignTexturePacket.Response> GET_TRAFFIC_SIGN_TEXTURE = NETWORK.registerSendAndReceivePacket("get_traffic_sign_texture", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> GetTrafficSignTexturePacket.handle(packet, context)), GetTrafficSignTexturePacket.Request::new, GetTrafficSignTexturePacket.Response::new);
    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, PatternCatalogueDeletePacket.Request, PatternCatalogueDeletePacket.Response> DELETE_PATTERN_CATALOG_ENTRY = NETWORK.registerSendAndReceivePacket("delete_pattern_catalog_entry", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> PatternCatalogueDeletePacket.handle(packet, context)), PatternCatalogueDeletePacket.Request::new, PatternCatalogueDeletePacket.Response::new);
    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, TrafficSignPatternPacket.Request, TrafficSignPatternPacket.Response> UPDATE_TRAFFIC_SIGN_PATTERN = NETWORK.registerSendAndReceivePacket("update_traffic_sign_pattern", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> TrafficSignPatternPacket.handle(packet, context)), TrafficSignPatternPacket.Request::new, TrafficSignPatternPacket.Response::new);
    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, CreateNewTrafficSignTexturePacket.Request, CreateNewTrafficSignTexturePacket.Response> CREATE_NEW_TRAFFIC_SIGN_TEXTURE = NETWORK.registerSendAndReceivePacket("create_new_traffic_sign_texture", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> CreateNewTrafficSignTexturePacket.handle(packet, context)), CreateNewTrafficSignTexturePacket.Request::new, CreateNewTrafficSignTexturePacket.Response::new);
    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, ColorPaletteItemPacket.Request, ColorPaletteItemPacket.Response> UPDATE_COLOR_PALETTE_ITEM = NETWORK.registerSendAndReceivePacket("update_color_palette_item", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> ColorPaletteItemPacket.handle(packet, context)), ColorPaletteItemPacket.Request::new, ColorPaletteItemPacket.Response::new);
    public static final NetworkPacketType.SendAndReceive<NetworkDirection.C2S, PatternCatalogueIndexPacketGui.Request, PatternCatalogueIndexPacketGui.Response> UPDATE_PATTERN_CATALOG_INDEX_IN_GUI = NETWORK.registerSendAndReceivePacket("update_pattern_catalog_index_in_gui", NetworkDirection.C2S, (packet, context) -> context.queueResult(() -> PatternCatalogueIndexPacketGui.handle(packet, context)), PatternCatalogueIndexPacketGui.Request::new, PatternCatalogueIndexPacketGui.Response::new);

    public static final NetworkPacketType.Send<NetworkDirection.C2S, CreativePatternCataloguePacket> UPDATE_CREATIVE_PATTERN_CATALOG_ITEM = NETWORK.registerSendOnlyPacket("update_creative_pattern_catalog_item", NetworkDirection.C2S, (packet, context) -> context.queue(() -> CreativePatternCataloguePacket.handle(packet, context)), CreativePatternCataloguePacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, LinkerModePacket> UPDATE_LINK_MODE = NETWORK.registerSendOnlyPacket("update_link_mode", NetworkDirection.C2S, (packet, context) -> context.queue(() -> LinkerModePacket.handle(packet, context)), LinkerModePacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, LedDevicePacket> UPDATE_LED_DEVICE = NETWORK.registerSendOnlyPacket("update_led_device", NetworkDirection.C2S, (packet, context) -> context.queue(() -> LedDevicePacket.handle(packet, context)), LedDevicePacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, PaintBrushPacket> UPDATE_PAINT_BRUSH = NETWORK.registerSendOnlyPacket("update_paint_brush", NetworkDirection.C2S, (packet, context) -> context.queue(() -> PaintBrushPacket.handle(packet, context)), PaintBrushPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, PatternCatalogueIndexPacket> UPDATE_PATTERN_CATALOG_INDEX = NETWORK.registerSendOnlyPacket("update_pattern_catalog_index", NetworkDirection.C2S, (packet, context) -> context.queue(() -> PatternCatalogueIndexPacket.handle(packet, context)), PatternCatalogueIndexPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, RoadBuilderBuildRoadPacket> ROAD_BUILDER_BUILD_ROAD = NETWORK.registerSendOnlyPacket("road_builder_build_road", NetworkDirection.C2S, (packet, context) -> context.queue(() -> RoadBuilderBuildRoadPacket.handle(packet, context)), RoadBuilderBuildRoadPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, RoadBuilderDataPacket> UPDATE_ROAD_BUILDER = NETWORK.registerSendOnlyPacket("update_road_builder", NetworkDirection.C2S, (packet, context) -> context.queue(() -> RoadBuilderDataPacket.handle(packet, context)), RoadBuilderDataPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, RoadBuilderResetPacket> RESET_ROAD_BUILDER = NETWORK.registerSendOnlyPacket("reset_road_builder", NetworkDirection.C2S, (packet, context) -> context.queue(() -> RoadBuilderResetPacket.handle(packet, context)), RoadBuilderResetPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, StreetLampConfigPacket> UPDATE_STREET_LAMP_CONFIG_CARD = NETWORK.registerSendOnlyPacket("update_street_lamp_config_card", NetworkDirection.C2S, (packet, context) -> context.queue(() -> StreetLampConfigPacket.handle(packet, context)), StreetLampConfigPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, TownSignPacket> UPDATE_TOWN_SIGN = NETWORK.registerSendOnlyPacket("update_town_sign", NetworkDirection.C2S, (packet, context) -> context.queue(() -> TownSignPacket.handle(packet, context)), TownSignPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, TrafficLightControllerPacket> UPDATE_TRAFFIC_LIGHT_CONTROLLER = NETWORK.registerSendOnlyPacket("update_traffic_light_controller", NetworkDirection.C2S, (packet, context) -> context.queue(() -> TrafficLightControllerPacket.handle(packet, context)), TrafficLightControllerPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, TrafficLightPacket> UPDATE_TRAFFIC_LIGHT_PACKET = NETWORK.registerSendOnlyPacket("update_traffic_light_packet", NetworkDirection.C2S, (packet, context) -> context.queue(() -> TrafficLightPacket.handle(packet, context)), TrafficLightPacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, TrafficLightSchedulePacket> UPDATE_TRAFFIC_LIGHT_SCHEDULE = NETWORK.registerSendOnlyPacket("update_traffic_light_schedule", NetworkDirection.C2S, (packet, context) -> context.queue(() -> TrafficLightSchedulePacket.handle(packet, context)), TrafficLightSchedulePacket::new);
    public static final NetworkPacketType.Send<NetworkDirection.C2S, WritableSignPacket> UPDATE_WRITABLE_SIGN = NETWORK.registerSendOnlyPacket("update_writable_sign", NetworkDirection.C2S, (packet, context) -> context.queue(() -> WritableSignPacket.handle(packet, context)), WritableSignPacket::new);
    
    
    
    public static final NetworkPacketType.Send<NetworkDirection.S2C, TrafficSignTextureResetPacket> RESET_TRAFFIC_SIGN_TEXTURE = NETWORK.registerSendOnlyPacket("reset_traffic_sign_texture", NetworkDirection.S2C, (packet, context) -> context.queue(() -> TrafficSignTextureResetPacket.handle(packet, context)), TrafficSignTextureResetPacket::new);

    

    public static void init() {}
}
