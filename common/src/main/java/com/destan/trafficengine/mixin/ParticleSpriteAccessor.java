package com.destan.trafficengine.mixin;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(TextureSheetParticle.class)
public interface ParticleSpriteAccessor {
    @Invoker("setSprite") void trafficengine$setSprite(TextureAtlasSprite sprite);
}
