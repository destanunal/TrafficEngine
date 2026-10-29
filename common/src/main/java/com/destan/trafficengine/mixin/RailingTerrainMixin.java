package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bounded event-driven attachment invalidation for changes to layered road surfaces. */
@Mixin(Level.class)
public abstract class RailingTerrainMixin {
    @ModifyExpressionValue(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState trafficengine$rampChanged(BlockState previous,BlockPos pos,BlockState updated,int flags,int limit){
        if(previous!=null&&previous!=updated&&RailingGeometry.layerHeightChanged(previous,updated))SlopedPedestrianRailingBlock.terrainChanged((Level)(Object)this,pos);
        return previous;
    }

}
