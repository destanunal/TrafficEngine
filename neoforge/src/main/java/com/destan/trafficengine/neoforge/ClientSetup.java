package com.destan.trafficengine.neoforge;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.client.tooltip.ClientTrafficSignTooltipStack;
import com.destan.trafficengine.client.tooltip.TrafficSignTooltip;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.destan.trafficengine.client.screen.TrafficSignWorkbenchGui;
import com.destan.trafficengine.client.screen.menu.ModMenuTypes;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = TrafficEngine.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientSetup {

	@SubscribeEvent
	public static void onRegisterTooltipEvent(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(TrafficSignTooltip.class, (tooltip) -> {
			return new ClientTrafficSignTooltipStack(tooltip);
		});
	}
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TRAFFIC_SIGN_WORKBENCH_MENU.get(), TrafficSignWorkbenchGui::new);
    }
}
