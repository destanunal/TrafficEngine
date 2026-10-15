package com.destan.trafficengine.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext", remap=false)
public interface EmbeddiumBlockContextAccessor {
    @Invoker(value="pos", remap=false) BlockPos trafficengine$position();
}
