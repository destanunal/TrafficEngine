package com.destan.trafficengine.mixin;

import org.joml.Vector3f;
import com.mojang.math.Transformation;
import com.destan.trafficengine.client.model.EmissiveFace;
import com.destan.trafficengine.client.model.EmissiveQuad;
import com.destan.trafficengine.client.model.PlacementShadeQuad;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FaceBakery.class)
public abstract class EmissiveFaceBakeryMixin {
    @Shadow private void applyElementRotation(Vector3f position, BlockElementRotation rotation) {}
    @Shadow public abstract void applyModelRotation(Vector3f position, Transformation rotation);

    @Inject(method="bakeQuad",at=@At("RETURN"),cancellable=true)
    private void trafficengine$bake(Vector3f from, Vector3f to, BlockElementFace face,
            TextureAtlasSprite sprite, Direction direction, ModelState state,
            BlockElementRotation rotation, boolean shade, ResourceLocation model,
            CallbackInfoReturnable<BakedQuad> cir) {
        EmissiveFace metadata = (EmissiveFace) face;
        BakedQuad quad = cir.getReturnValue();
        float[] corners = metadata.trafficengine$getVertices();
        if (corners != null) {
            int[] packed = quad.getVertices().clone();
            int stride = packed.length / 4;
            Vector3f[] positions = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                Vector3f position = new Vector3f(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]).div(16);
                // Reuse Minecraft's transforms for block facing, diagonal placement and items.
                applyElementRotation(position, rotation);
                applyModelRotation(position, state.getRotation());
                positions[i] = position;
                packed[i * stride] = Float.floatToRawIntBits(position.x());
                packed[i * stride + 1] = Float.floatToRawIntBits(position.y());
                packed[i * stride + 2] = Float.floatToRawIntBits(position.z());
            }
            Vector3f normal = new Vector3f(positions[1]).sub(positions[0])
                    .cross(new Vector3f(positions[2]).sub(positions[0]));
            // The rounded ends use a triangle represented by a quad with one repeated corner.
            if (normal.lengthSquared() < 1.0e-12f) {
                normal.set(positions[2]).sub(positions[0])
                        .cross(new Vector3f(positions[3]).sub(positions[0]));
            }
            if (normal.lengthSquared() > 1.0e-12f) normal.normalize();
            else normal.set(0, 1, 0);
            int packedNormal = ((Math.round(normal.x() * 127) & 255))
                    | ((Math.round(normal.y() * 127) & 255) << 8)
                    | ((Math.round(normal.z() * 127) & 255) << 16);
            for (int i = 0; i < 4; i++) packed[i * stride + 7] = packedNormal;
            quad = new BakedQuad(packed, quad.getTintIndex(),
                    Direction.getNearest(normal.x(), normal.y(), normal.z()), sprite, shade);
            cir.setReturnValue(quad);
        }
        if (metadata.trafficengine$matchesStraightShade() && !metadata.trafficengine$isEmissive()) {
            // Keep the real normal/geometry. Only the terrain renderer's shade lookup
            // uses the facing this face would have without the extra diagonal turn.
            Vector3f reference = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            reference.add(0.5f, 0.5f, 0.5f);
            applyModelRotation(reference, state.getRotation());
            reference.sub(0.5f, 0.5f, 0.5f);
            quad = new PlacementShadeQuad(quad, Direction.getNearest(reference.x(), reference.y(), reference.z()));
            cir.setReturnValue(quad);
        }
        if (metadata.trafficengine$isEmissive()) cir.setReturnValue(new EmissiveQuad(quad));
    }
}
