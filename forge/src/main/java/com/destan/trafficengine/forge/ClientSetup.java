package com.destan.trafficengine.forge;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.client.tooltip.ClientTrafficSignTooltipStack;
import com.destan.trafficengine.client.tooltip.TrafficSignTooltip;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TrafficEngine.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

	@SubscribeEvent
	public static void onRegisterTooltipEvent(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(TrafficSignTooltip.class, (tooltip) -> {
			return new ClientTrafficSignTooltipStack(tooltip);
		});
	}
}
