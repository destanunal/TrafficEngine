package com.destan.trafficengine.registry;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.item.BrushItem;
import com.destan.trafficengine.item.ColorPaletteItem;
import com.destan.trafficengine.item.CreativePatternCatalogueItem;
import com.destan.trafficengine.item.HammerItem;
import com.destan.trafficengine.item.PatternCatalogueItem;
import com.destan.trafficengine.item.RoadConstructionTool;
import com.destan.trafficengine.item.StreetLampConfigCardItem;
import com.destan.trafficengine.item.TrafficLightLinkerItem;
import com.destan.trafficengine.item.WrenchItem;
import com.destan.trafficengine.recipe.DamageableItemRecipe;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import dev.architectury.extensions.injected.InjectedItemPropertiesExtension;

public class ModItems {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(TrafficEngine.MOD_ID, Registries.ITEM);


    public static final RegistrySupplier<Item> WRENCH = ITEMS.register("wrench", WrenchItem::new);
    public static final RegistrySupplier<Item> TRAFFIC_LIGHT_LINKER = ITEMS.register("traffic_light_linker", () -> new TrafficLightLinkerItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB).stacksTo(1)));
//    public static final RegistrySupplier<Item> BITUMEN = ITEMS.register("raw_bitumen", () -> new Item(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> IRON_ROD = ITEMS.register("iron_rod", () -> new Item(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> IRON_PLATE = ITEMS.register("iron_plate", () -> new Item(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> PAINT_BRUSH = ITEMS.register("paint_brush", () -> new BrushItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB),0));
//    public static final RegistrySupplier<Item> WOOD_ROAD_CONSTRUCTION_TOOL = ITEMS.register("wood_road_construction_tool", () -> new RoadConstructionTool(Tiers.WOOD, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> STONE_ROAD_CONSTRUCTION_TOOL = ITEMS.register("stone_road_construction_tool", () -> new RoadConstructionTool(Tiers.STONE, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> IRON_ROAD_CONSTRUCTION_TOOL = ITEMS.register("iron_road_construction_tool", () -> new RoadConstructionTool(Tiers.IRON, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> GOLD_ROAD_CONSTRUCTION_TOOL = ITEMS.register("gold_road_construction_tool", () -> new RoadConstructionTool(Tiers.GOLD, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> DIAMOND_ROAD_CONSTRUCTION_TOOL = ITEMS.register("diamond_road_construction_tool", () -> new RoadConstructionTool(Tiers.DIAMOND, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> NETHERITE_ROAD_CONSTRUCTION_TOOL = ITEMS.register("netherite_road_construction_tool", () -> new RoadConstructionTool(Tiers.NETHERITE, ((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> HAMMER = ITEMS.register("hammer", () -> new HammerItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> STREET_LAMP_CONFIG_CARD = ITEMS.register("street_lamp_config_card", () -> new StreetLampConfigCardItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> COLOR_PALETTE = ITEMS.register("color_palette", () -> new ColorPaletteItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
//    public static final RegistrySupplier<Item> PATTERN_CATALOGUE = ITEMS.register("pattern_catalogue", () -> new PatternCatalogueItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));
    public static final RegistrySupplier<Item> CREATIVE_PATTERN_CATALOGUE = ITEMS.register("creative_pattern_catalogue", () -> new CreativePatternCatalogueItem(((InjectedItemPropertiesExtension)new Item.Properties()).arch$tab(ModCreativeModeTab.MOD_TAB)));


    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(TrafficEngine.MOD_ID, Registries.RECIPE_SERIALIZER);
    public static final RegistrySupplier<RecipeSerializer<?>> DAMAGEABLE_ITEM_RECIPE = RECIPES.register("damageable_item_recipe", DamageableItemRecipe.Serializer::new);



    public static void register() {
        ITEMS.register();
        RECIPES.register();
    }

}
