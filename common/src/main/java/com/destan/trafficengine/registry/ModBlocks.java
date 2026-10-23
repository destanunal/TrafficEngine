package com.destan.trafficengine.registry;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import com.destan.trafficengine.Constants;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.StreetLampBaseBlock.LampType;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.item.WearableBlockItem;
import com.destan.trafficengine.item.LargeTrafficDisplayBlockItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(TrafficEngine.MOD_ID, Registries.BLOCK);
    public static List<RegistrySupplier<Block>> COLORED_BLOCKS = new ArrayList<>();
    public static HashMap<String, RegistrySupplier<Block>> ROAD_BLOCKS = new HashMap<>();
    public static final java.util.EnumMap<RoadType, RegistrySupplier<Block>> CONNECTED_ROADS = new java.util.EnumMap<>(RoadType.class);
    public static final java.util.EnumMap<RoadType, RegistrySupplier<Block>> CONNECTED_SLOPES = new java.util.EnumMap<>(RoadType.class);


    public static final RegistrySupplier<Block> ASPHALT = registerBlock("asphalt", () -> new AsphaltBlock(RoadType.ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CRACKED_ASPHALT = registerBlock("cracked_asphalt", () -> new AsphaltBlock(RoadType.CRACKED_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> HEAVY_CRACKED_ASPHALT = registerBlock("heavy_cracked_asphalt", () -> new AsphaltBlock(RoadType.HEAVY_CRACKED_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> LIGHT_ASPHALT = registerBlock("light_asphalt", () -> new AsphaltBlock(RoadType.LIGHT_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> DARK_ASPHALT = registerBlock("dark_asphalt", () -> new AsphaltBlock(RoadType.DARK_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> DIRTY_ASPHALT = registerBlock("dirty_asphalt", () -> new AsphaltBlock(RoadType.DIRTY_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ASPHALT_SLOPE = registerBlock("asphalt_slope", () -> new AsphaltSlope(RoadType.ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CRACKED_ASPHALT_SLOPE = registerBlock("cracked_asphalt_slope", () -> new AsphaltSlope(RoadType.CRACKED_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> HEAVY_CRACKED_ASPHALT_SLOPE = registerBlock("heavy_cracked_asphalt_slope", () -> new AsphaltSlope(RoadType.HEAVY_CRACKED_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> LIGHT_ASPHALT_SLOPE = registerBlock("light_asphalt_slope", () -> new AsphaltSlope(RoadType.LIGHT_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> DARK_ASPHALT_SLOPE = registerBlock("dark_asphalt_slope", () -> new AsphaltSlope(RoadType.DARK_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> DIRTY_ASPHALT_SLOPE = registerBlock("dirty_asphalt_slope", () -> new AsphaltSlope(RoadType.DIRTY_ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CONCRETE = registerBlock("concrete", () -> new AsphaltBlock(RoadType.CONCRETE), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CONCRETE_SLOPE = registerBlock("concrete_slope", () -> new AsphaltSlope(RoadType.CONCRETE), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> SIDEWALK = registerBlock("sidewalk", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE).requiresCorrectToolForDrops()), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> SIDEWALK_SLOPE = registerBlock("sidewalk_slope", SidewalkSlopeBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> SPEED_BUMP = registerBlock("speed_bump", () -> new SpeedBumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f).requiresCorrectToolForDrops()), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ASPHALT_SPEED_BUMP = registerColoredBlock("rubber_speed_bump", AsphaltSpeedBumpBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_ISLAND_BOLLARD = registerBlock("traffic_island_bollard", TrafficIslandBollardBlock::new, ModCreativeModeTab.MOD_TAB, false);

    public static final RegistrySupplier<Block> WIDE_SPEED_BUMP = registerBlock("wide_speed_bump", () -> new CustomSpeedBumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f).requiresCorrectToolForDrops(), Shapes.or(Block.box(0, 0, 1.17157287525381, 16, 0.5, 14.8284271247462), Block.box(0, 0.5, 2.37867965644036, 16, 1.25, 13.6213203435596), Block.box(0, 1.25, 4.18933982822018, 16, 2, 11.8106601717798)), Shapes.or(Block.box(1.17157287525381, 0, 0, 14.8284271247462, 0.5, 16), Block.box(2.37867965644036, 0.5, 0, 13.6213203435596, 1.25, 16), Block.box(4.18933982822018, 1.25, 0, 11.8106601717798, 2, 16))), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> BIKE_LANE_SEPARATOR = registerColoredBlock("bike_lane_separator", () -> new com.destan.trafficengine.block.BikeLaneSeparatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f).requiresCorrectToolForDrops().noOcclusion()), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CONCRETE_BARRIER = registerColoredBlock("concrete_barrier", () -> new ConcreteBarrierBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ASPHALT_CURB = registerBlock("asphalt_curb", () -> new AsphaltCurb(RoadType.ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CONCRETE_CURB = registerBlock("concrete_curb", () -> new AsphaltCurb(RoadType.CONCRETE), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> CONCRETE_CURB_SLOPE = registerBlock("concrete_curb_slope", () -> new AsphaltCurbSlope(RoadType.CONCRETE), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ASPHALT_CURB_SLOPE = registerBlock("asphalt_curb_slope", () -> new AsphaltCurbSlope(RoadType.ASPHALT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_SIGN_WORKBENCH = registerBlock("traffic_sign_workbench", () -> new TrafficSignWorkbenchBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ROAD_SALT = registerBlock("road_salt", () -> new RoadSaltBlock(), ModCreativeModeTab.MOD_TAB, false);

    static {
        for (RoadType s : RoadType.values()) {
            if (s == RoadType.NONE)
                continue;

            CONNECTED_ROADS.put(s, registerColoredBlockWithoutItem(s.getRoadType() + "_auto_line", () -> new ConnectedRoadBlock(s)));
            CONNECTED_SLOPES.put(s, registerColoredBlockWithoutItem(s.getRoadType() + "_slope_auto_line", () -> new ConnectedRoadSlopeBlock(s)));
            for (int i = 0; i < Constants.MAX_ASPHALT_PATTERNS; i++) {
                String id = s.getRoadType() + "_pattern_" + i;
                RegistrySupplier<Block> block = registerColoredBlockWithoutItem(id, () -> new PaintedAsphaltBlock(s, s.getBlock()));
                ROAD_BLOCKS.put(id, block);

                String id2 = s.getRoadType() + "_slope_pattern_" + i;
                RegistrySupplier<Block> block2 = registerColoredBlockWithoutItem(id2, () -> new PaintedAsphaltSlope(s, s.getSlope()));
                ROAD_BLOCKS.put(id2, block2);
            }
        }
    }

    public static final RegistrySupplier<Block> CHEVRON_PANEL = registerBlock("chevron_panel", ChevronPanelBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TEMPORARY_PEDESTRIAN_FENCE = registerBlock("temporary_pedestrian_fence", TemporaryPedestrianFenceBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> HIGHWAY_DIRECTION_SIGN = registerBlock("highway_direction_sign", () -> new RoadInformationSignBlock(RoadInformationSignBlock.Kind.HIGHWAY), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ROAD_DIRECTION_SIGN = registerBlock("road_direction_sign", () -> new RoadInformationSignBlock(RoadInformationSignBlock.Kind.DIRECTION), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> VARIABLE_SPEED_SIGN = registerBlock("variable_speed_sign", () -> new RoadInformationSignBlock(RoadInformationSignBlock.Kind.VARIABLE_SPEED), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_POLE_BASE = registerBlock("traffic_pole_base", com.destan.trafficengine.block.TrafficPoleBaseBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_SIGN_POST = registerBlock("traffic_sign_post", () -> new TrafficSignPostBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_SIGN = registerBlock("traffic_sign", () -> new TrafficSignBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> DOUBLE_TRAFFIC_SIGN = registerBlock("double_traffic_sign", () -> new DoubleSidedTrafficSignBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TOWN_SIGN = registerBlock("town_sign", () -> new TownSignBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> STREET_SIGN = registerColoredBlock("street_sign", () -> new StreetSignBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> HOUSE_NUMBER_SIGN = registerColoredBlock("house_number_sign", () -> new HouseNumberSignBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_LIGHT = registerColoredBlock("traffic_light", () -> new TrafficLightBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> HORIZONTAL_TRAFFIC_LIGHT = registerColoredBlock("horizontal_traffic_light", () -> new HorizontalTrafficLightBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_LIGHT_CONTROLLER = registerBlock("traffic_light_controller", () -> new TrafficLightControllerBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> TRAFFIC_LIGHT_REQUEST_BUTTON = registerBlock("traffic_light_request_button", () -> new TrafficLightRequestButtonBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> STREET_LAMP = registerColoredBlock("street_lamp", () -> new StreetLampBaseBlock(LampType.NORMAL), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> DOUBLE_STREET_LAMP = registerColoredBlock("double_street_lamp", () -> new StreetLampBaseBlock(LampType.DOUBLE), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> SMALL_STREET_LAMP = registerColoredBlock("small_street_lamp", () -> new StreetLampBaseBlock(LampType.SMALL), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> SMALL_DOUBLE_STREET_LAMP = registerColoredBlock("small_double_street_lamp", () -> new StreetLampBaseBlock(LampType.SMALL_DOUBLE), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> STREET_LIGHT = registerColoredBlock("street_light", () -> new StreetLightBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> FLUORESCENT_TUBE_LAMP = registerColoredBlock("fluorescent_tube_lamp", () -> new FluorescentTubeLampBlock(), ModCreativeModeTab.MOD_TAB, false);

    public static final RegistrySupplier<Block> WHITE_DELINEATOR = registerBlock("white_delineator", () -> new DelineatorBlock(false), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> YELLOW_DELINEATOR = registerBlock("yellow_delineator", () -> new DelineatorBlock(false), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> RED_DELINEATOR = registerBlock("red_delineator", () -> new DelineatorBlock(false), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> SMALL_WHITE_DELINEATOR = registerBlock("small_white_delineator", () -> new DelineatorBlock(true), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> SMALL_YELLOW_DELINEATOR = registerBlock("small_yellow_delineator", () -> new DelineatorBlock(true), ModCreativeModeTab.MOD_TAB, false);

    public static final RegistrySupplier<Block> GUARDRAIL = registerColoredBlock("guardrail", () -> new GuardrailBlock(), ModCreativeModeTab.MOD_TAB, false);

    public static final RegistrySupplier<Block> PAINT_BUCKET = registerColoredBlock("paint_bucket", () -> new PaintBucketBlock(), ModCreativeModeTab.MOD_TAB, true);

    public static final RegistrySupplier<Block> TRAFFIC_CONE = registerColoredBlock("traffic_cone", () -> new TrafficConeBlock(), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> TRAFFIC_BOLLARD = registerColoredBlock("traffic_bollard", () -> new TrafficBollardBlock(), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> TRAFFIC_BARREL = registerColoredBlock("traffic_barrel", () -> new TrafficBarrelBlock(), ModCreativeModeTab.MOD_TAB, true);
    public static final RegistrySupplier<Block> ROAD_BARRIER_FENCE = registerColoredBlock("road_barrier_fence", () -> new RoadBarrierFenceBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> BOOM_BARRIER = registerBlock("boom_barrier", com.destan.trafficengine.block.BoomBarrierBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> BOOM_BARRIER_PART = registerBlockWithoutItem("boom_barrier_part", com.destan.trafficengine.block.BoomBarrierPartBlock::new);
    public static final RegistrySupplier<Block> RETRACTABLE_BARRIER = registerBlock("retractable_barrier", () -> new RetractableBarrierBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> LED_DEVICE_PART = registerBlockWithoutItem("led_device_part", LedDevicePartBlock::new);
    public static final RegistrySupplier<Block> TRAFFIC_DISPLAY = registerBlock("traffic_display", () -> new LedDeviceBlock(LedDeviceType.TRAFFIC_DISPLAY), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> LARGE_TRAFFIC_DISPLAY = registerBlock("large_traffic_display", () -> new LedDeviceBlock(LedDeviceType.LARGE_TRAFFIC_DISPLAY), ModCreativeModeTab.MOD_TAB, LargeTrafficDisplayBlockItem.class);
    public static final RegistrySupplier<Block> WARNING_LIGHT = registerBlock("warning_light", WarningLightBlock::new, ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> LED_LIGHT = registerBlock("led_light", () -> new LedDeviceBlock(LedDeviceType.LED_LIGHT), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> REFLECTOR = registerColoredBlock("reflector", () -> new ReflectorBlock(), ModCreativeModeTab.MOD_TAB, false);

    public static final RegistrySupplier<Block> MANHOLE = registerBlock("manhole", () -> new ManholeBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> MANHOLE_COVER = registerBlock("manhole_cover", () -> new ManholeCoverBlock(), ModCreativeModeTab.MOD_TAB, false);
    public static final RegistrySupplier<Block> ROAD_GULLY = registerBlock("road_gully", () -> new ManholeCoverBlock(), ModCreativeModeTab.MOD_TAB, false);

    private static <T extends Block>RegistrySupplier<T> registerBlockWithoutItem(String name, Supplier<T> block) {
        RegistrySupplier<T> toReturn = BLOCKS.register(name, block);
        return toReturn;
    }

    private static <T extends Block>RegistrySupplier<Block> registerColoredBlockWithoutItem(String name, Supplier<Block> block) {
        RegistrySupplier<Block> toReturn = registerBlockWithoutItem(name, block);
        COLORED_BLOCKS.add(toReturn);
        return toReturn;
    }

    private static <T extends Block>RegistrySupplier<Block> registerColoredBlock(String name, Supplier<Block> block, RegistrySupplier<CreativeModeTab> tab, boolean wearable) {
        RegistrySupplier<Block> toReturn = registerBlock(name, block, tab, wearable);
        COLORED_BLOCKS.add(toReturn);
        return toReturn;
    }

    private static <T extends Block>RegistrySupplier<T> registerBlock(String name, Supplier<T> block, RegistrySupplier<CreativeModeTab> tab, boolean wearable) {
        RegistrySupplier<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, tab, wearable);
        return toReturn;
    }

    @SuppressWarnings("unused")
    private static <T extends Block, I extends BlockItem>RegistrySupplier<T> registerBlock(String name, Supplier<T> block, RegistrySupplier<CreativeModeTab> tab, Class<I> blockItemClass) {
        RegistrySupplier<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, tab, blockItemClass);
        return toReturn;
    }

    @SuppressWarnings("unused")
    private static <T extends Block>RegistrySupplier<T> registerBlockWithCustomItemId(String name, String itemId, Supplier<T> block, RegistrySupplier<CreativeModeTab> tab, boolean wearable) {
        RegistrySupplier<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(itemId, toReturn, tab, wearable);
        return toReturn;
    }

    private static <T extends Block>RegistrySupplier<Item> registerBlockItem(String name, RegistrySupplier<T> block, RegistrySupplier<CreativeModeTab> tab, boolean wearable) {
        if (wearable) {
            return ModItems.ITEMS.register(name, () -> new WearableBlockItem(block.get(), new Item.Properties()));
        }

        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block, I extends BlockItem>RegistrySupplier<Item> registerBlockItem(String name, RegistrySupplier<T> block, RegistrySupplier<CreativeModeTab> tab, Class<I> blockItemClass) {
        return ModItems.ITEMS.register(name, () -> {
            try {
                return blockItemClass.getDeclaredConstructor(Block.class, Item.Properties.class).newInstance(block.get(), new Item.Properties());
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
                return new BlockItem(block.get(), new Item.Properties());
            }
        });
    }

    public static void register() {
        BLOCKS.register();
    }

}
