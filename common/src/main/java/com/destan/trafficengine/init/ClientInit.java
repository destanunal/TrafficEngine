package com.destan.trafficengine.init;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;


import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.entity.HouseNumberSignBlockEntity;
import com.destan.trafficengine.block.entity.StreetSignBlockEntity;
import com.destan.trafficengine.client.TintedTextures;
import com.destan.trafficengine.client.ber.TownSignBlockEntityRenderer;
import com.destan.trafficengine.client.ber.RoadSignGraphics;
import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import com.destan.trafficengine.client.ber.HorizontalTrafficLightBlockEntityRenderer;
import com.destan.trafficengine.client.ber.TrafficLightBlockEntityRenderer;
import com.destan.trafficengine.client.ber.LedDeviceBlockEntityRenderer;
import com.destan.trafficengine.client.ber.TrafficSignBlockEntityRenderer;
import com.destan.trafficengine.client.ber.WritableSignBlockEntityRenderer;
import com.destan.trafficengine.client.screen.TrafficSignWorkbenchGui;
import com.destan.trafficengine.client.screen.menu.ModMenuTypes;
import com.destan.trafficengine.client.tooltip.ClientTrafficSignTooltipStack;
import com.destan.trafficengine.client.tooltip.TrafficSignTooltip;
import com.destan.trafficengine.data.TrafficSignClientTexture;
import com.destan.trafficengine.item.IScrollEventItem;
import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItems;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.injectables.annotations.PlatformOnly;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ClientInit {
    private static final Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> tooltipComponentFactories = new ConcurrentHashMap<>();


    /**
     * Register a factory for ClientTooltipComponents.
     * @param cls the class for the component
     * @param factory the factory for the ClientTooltipComponent
     */
    @PlatformOnly(value = PlatformOnly.FABRIC)
    @SuppressWarnings("unchecked")
    public static <T extends TooltipComponent> void registerTooltipComponentFactory(Class<T> cls, Function<? super T, ? extends ClientTooltipComponent> factory) {
        tooltipComponentFactories.put(cls, (Function<TooltipComponent, ClientTooltipComponent>) factory);
    }

    @PlatformOnly(value = PlatformOnly.FABRIC)
    @Nullable
    public static ClientTooltipComponent getClientTooltipComponent(TooltipComponent component) {
        var factory = tooltipComponentFactories.get(component.getClass());
        return factory == null ? null : factory.apply(component);
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        ClientTickEvent.CLIENT_POST.register(com.destan.trafficengine.client.lighting.ClientColoredLighting::tick);
        ClientTickEvent.CLIENT_POST.register(mc -> {
            TrafficLightBlockEntityRenderer.flushTimerCache();
            HorizontalTrafficLightBlockEntityRenderer.flushTimerCache();
        });
        ClientLifecycleEvent.CLIENT_SETUP.register(mc -> {

            
            /* RENDER LAYERS */
            RenderTypeRegistry.register(RenderType.cutout(),
                ModBlocks.WHITE_DELINEATOR.get(),
                ModBlocks.YELLOW_DELINEATOR.get(),
                ModBlocks.SMALL_WHITE_DELINEATOR.get(),
                ModBlocks.SMALL_YELLOW_DELINEATOR.get(),
                ModBlocks.RED_DELINEATOR.get(),
                ModBlocks.REFLECTOR.get(),
                ModBlocks.TRAFFIC_LIGHT_REQUEST_BUTTON.get(),
                ModBlocks.PAINT_BUCKET.get(),
                ModBlocks.MANHOLE.get(),
                ModBlocks.MANHOLE_COVER.get(),
                ModBlocks.TRAFFIC_SIGN_WORKBENCH.get(),
                    ModBlocks.HORIZONTAL_TRAFFIC_LIGHT.get()
            );
            RenderTypeRegistry.register(RenderType.translucent(), 
                ModBlocks.ROAD_SALT.get()
            );
            RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.COLORED_BLOCKS.stream().filter(x -> x.getId().toString().contains("pattern")).map(RegistrySupplier::get).toArray(Block[]::new));


            RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.CONNECTED_ROADS.values().stream().map(RegistrySupplier::get).toArray(Block[]::new));
            RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.CONNECTED_SLOPES.values().stream().map(RegistrySupplier::get).toArray(Block[]::new));
            /* BLOCK ENTITY RENDERERS */
            BlockEntityRendererRegistry.register(ModBlockEntities.BOOM_BARRIER_BLOCK_ENTITY.get(), com.destan.trafficengine.client.ber.BoomBarrierRenderer::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.ROAD_INFORMATION_SIGN_BLOCK_ENTITY.get(), com.destan.trafficengine.client.ber.RoadInformationSignRenderer::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.TOWN_SIGN_BLOCK_ENTITY.get(), TownSignBlockEntityRenderer::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.STREET_SIGN_BLOCK_ENTITY.get(), WritableSignBlockEntityRenderer<StreetSignBlockEntity>::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.HOUSE_NUMBER_SIGN_BLOCK_ENTITY.get(), WritableSignBlockEntityRenderer<HouseNumberSignBlockEntity>::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.TRAFFIC_SIGN_BLOCK_ENTITY.get(), TrafficSignBlockEntityRenderer::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.TRAFFIC_LIGHT_BLOCK_ENTITY.get(), TrafficLightBlockEntityRenderer::new);
            BlockEntityRendererRegistry.register(ModBlockEntities.LED_DEVICE_BLOCK_ENTITY.get(), LedDeviceBlockEntityRenderer::new);
            
            if (Platform.isFabric()) {
                ClientInit.registerTooltipComponentFactory(TrafficSignTooltip.class, (tooltip) -> {
                    return new ClientTrafficSignTooltipStack(tooltip);
                });
            }

            /* REGISTER MENUS */
            MenuScreens.register(ModMenuTypes.TRAFFIC_SIGN_WORKBENCH_MENU.get(), TrafficSignWorkbenchGui::new);

            /* REGISTER CUSTOM ITEM PROPERTIES */

            ItemPropertiesRegistry.register(ModItems.PAINT_BRUSH.get(), new ResourceLocation(TrafficEngine.MOD_ID, "paint"), (itemStack, world, entity, id) -> {
                CompoundTag nbt = itemStack.getTag();
                if (nbt != null) {
                    return nbt.getInt("paint");
                }
                return 0;
            });

            ItemPropertiesRegistry.register(ModItems.TRAFFIC_LIGHT_LINKER.get(), new ResourceLocation(TrafficEngine.MOD_ID, "mode"), (itemStack, world, entity, id) -> {
                CompoundTag nbt = itemStack.getTag();
                if (nbt != null) {
                    return nbt.getInt("Mode");
                }
                return 0;
            });
        });

        /* BLOCK COLORS */

        ClientLifecycleEvent.CLIENT_STARTED.register(mc -> {
            ColorHandlerRegistry.registerBlockColors(new TintedTextures.TintedBlock(), ModBlocks.COLORED_BLOCKS.toArray(RegistrySupplier[]::new));
            ColorHandlerRegistry.registerBlockColors((state,level,pos,index) -> {
                if(index!=110)return 0xFFFFFFFF;
                int theme=level!=null && pos!=null && level.getBlockEntity(pos) instanceof RoadInformationSignBlockEntity sign?sign.theme():0;
                return RoadSignGraphics.backgroundColor(((RoadInformationSignBlock)state.getBlock()).kind(),theme);
            },ModBlocks.HIGHWAY_DIRECTION_SIGN,ModBlocks.ROAD_DIRECTION_SIGN);
            ColorHandlerRegistry.registerItemColors((stack,index) -> {
                if(index!=110)return 0xFFFFFFFF;
                CompoundTag data=stack.getTagElement("BlockEntityTag");
                return RoadSignGraphics.backgroundColor(RoadInformationSignBlock.Kind.HIGHWAY,data==null?0:data.getInt("theme"));
            },ModBlocks.HIGHWAY_DIRECTION_SIGN,ModBlocks.ROAD_DIRECTION_SIGN);
            ColorHandlerRegistry.registerItemColors(new TintedTextures.TintedItem(), 
                ModBlocks.GUARDRAIL,
                ModBlocks.BIKE_LANE_SEPARATOR,
                ModBlocks.ASPHALT_SPEED_BUMP,
                ModItems.PAINT_BRUSH,
                ModBlocks.TRAFFIC_CONE,
                ModBlocks.TRAFFIC_BOLLARD,
                ModBlocks.TRAFFIC_BARREL,
                ModBlocks.ROAD_BARRIER_FENCE,
                ModBlocks.CONCRETE_BARRIER
            );
            ColorHandlerRegistry.registerItemColors((stack, index) -> switch (index) {
                case 100 -> 0xFF53616B;
                case 101 -> 0xFFA8B4BC;
                case 102 -> 0xFF070B10;
                case 103 -> 0xFF8B1928;
                case 104 -> 0xFFFF253E;
                case 105 -> 0xFFFFA092;
                case 106 -> 0xFFFFFFEE;
                default -> 0xFFFFFFFF;
            }, ModBlocks.VARIABLE_SPEED_SIGN);


        });

        ClientTickEvent.CLIENT_LEVEL_POST.register(level -> {            
            RoadConstructionTool.clientTick();
        });

        ClientRawInputEvent.MOUSE_SCROLLED.register((mc, delta) -> {
            LocalPlayer player = mc.player;

            if (player == null || delta == 0) {
                return EventResult.pass();
            }

            ItemStack stack = player.getMainHandItem() == null ? player.getOffhandItem() : player.getMainHandItem();
            if (stack != null && stack.getItem() instanceof IScrollEventItem item) {
                if (item.mouseScroll(player, stack, delta)) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        ClientGuiEvent.DEBUG_TEXT_LEFT.register(list -> {
            list.add(String.format("TE | T: %s",
                TrafficSignClientTexture.debug_cachedTexturesCount()
            ));
        });
        
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            TrafficLightBlockEntityRenderer.clearRuntimeCache();
            HorizontalTrafficLightBlockEntityRenderer.clearRuntimeCache();
            TrafficEngine.LOGGER.info("Cleaning up traffic sign texture cache...");
            int count = TrafficSignClientTexture.closeAll();
            TrafficEngine.LOGGER.info("All " + count + " loaded custom traffic sign textures have been closed.");
        });
    }
    
}
