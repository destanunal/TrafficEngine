package com.destan.trafficengine.mixin;

import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only an actual change in RGB occlusion invalidates volumes; no per-call capture objects. */
@Mixin(Level.class)
public abstract class ColoredLightWorldMixin {
    @ModifyExpressionValue(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState trafficengine$occlusionChanged(BlockState previous, BlockPos pos, BlockState updated, int flags, int limit) {
        if (previous != null && (Object)this instanceof ClientLevel world
                && previous.isSolidRender(world,pos) != updated.isSolidRender(world,pos)) ClientColoredLighting.blockChanged(pos);
        return previous;
    }
}
