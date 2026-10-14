package com.destan.trafficengine.mixin.fabric;
import com.destan.trafficengine.client.lighting.ClientColoredLighting;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Covers Fabric's renderer API as well as the common vanilla path. */
@Mixin(value=AbstractBlockRenderContext.class,remap=false)
public class ColoredLightIndigoMixin {
    @Shadow @Final protected BlockRenderInfo blockInfo;
    @Inject(method="shadeQuad",at=@At("TAIL"))
    private void trafficengine$coloredLight(MutableQuadViewImpl quad,boolean a,boolean b,boolean c,CallbackInfo ci) {
        if(quad.material().emissive())return;
        var tint=ClientColoredLighting.tint(blockInfo.blockPos,quad.lightFace(),quad.lightmap(0),quad.lightmap(1),quad.lightmap(2),quad.lightmap(3));
        if(tint==null)return;
        for(int i=0;i<4;i++) {
            int color=quad.color(i);
            int r=Math.round((color>>16&255)*tint.red()),g=Math.round((color>>8&255)*tint.green()),blue=Math.round((color&255)*tint.blue());
            quad.color(i,(color&0xFF000000)|(r<<16)|(g<<8)|blue);
            int light=quad.lightmap(i),minimum=tint.brightness()<<4;
            if((light&0xF0)<minimum)quad.lightmap(i,(light&~0xF0)|minimum);
        }
    }
}
