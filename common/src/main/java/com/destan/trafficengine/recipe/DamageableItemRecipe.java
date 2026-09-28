package com.destan.trafficengine.recipe;

import com.destan.trafficengine.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public class DamageableItemRecipe extends ShapelessRecipe {
    public DamageableItemRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.DAMAGEABLE_ITEM_RECIPE.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            Item item = stack.getItem();
            if (item instanceof IDamageableCraftingItem) {
                int newDamage = stack.getDamageValue() + 1;
                if (newDamage < stack.getMaxDamage()) {
                    ItemStack copy = stack.copy();
                    copy.setDamageValue(newDamage);
                    remaining.set(i, copy);
                }
            } else if (item.hasCraftingRemainingItem()) {
                remaining.set(i, new ItemStack(item.getCraftingRemainingItem()));
            }
        }
        return remaining;
    }

    public static class Serializer implements RecipeSerializer<DamageableItemRecipe> {
        private static DamageableItemRecipe fromVanilla(ShapelessRecipe recipe) {
            return new DamageableItemRecipe(recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients());
        }

        private static ShapelessRecipe toVanilla(DamageableItemRecipe recipe) {
            return new ShapelessRecipe(recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients());
        }

        @Override
        public MapCodec<DamageableItemRecipe> codec() {
            return RecipeSerializer.SHAPELESS_RECIPE.codec().xmap(Serializer::fromVanilla, Serializer::toVanilla);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DamageableItemRecipe> streamCodec() {
            return RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(Serializer::fromVanilla, Serializer::toVanilla);
        }
    }
}
