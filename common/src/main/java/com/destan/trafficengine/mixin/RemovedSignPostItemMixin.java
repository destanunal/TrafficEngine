package com.destan.trafficengine.mixin;

import com.destan.trafficengine.util.SignPostMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class RemovedSignPostItemMixin {
    @Inject(method="parse", at=@At("HEAD"))
    private static void migrate(net.minecraft.core.HolderLookup.Provider provider, net.minecraft.nbt.Tag data,
            CallbackInfoReturnable<java.util.Optional<ItemStack>> callback) {
        if (data instanceof CompoundTag tag) SignPostMigration.item(tag);
    }
}
