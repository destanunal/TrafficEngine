package com.destan.trafficengine.util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
/** Version-neutral blueprint item storage; reads cannot mutate server data on failed validation. */
public final class ItemData {
 private ItemData(){}
 public static Object identity(ItemStack stack){return stack.getTag();}
 public static CompoundTag get(ItemStack stack){return stack.getTag()==null?null:stack.getTag().copy();}
 public static void set(ItemStack stack,CompoundTag tag){stack.setTag(tag);}
}
