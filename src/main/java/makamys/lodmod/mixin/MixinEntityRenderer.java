package makamys.lodmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.lodmod.LODMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer {
    
    @Shadow
    private float farPlaneDistance;
    
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", shift = At.Shift.AFTER, ordinal = 0))
    private void onConstructed(CallbackInfo ci) {
        if(LODMod.isActive()) {
            farPlaneDistance *= LODMod.renderer.getFarPlaneDistanceMultiplier();
        }
    }
    
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V", shift = At.Shift.AFTER, ordinal = 1))
    private void afterSortAndRender(float alpha, long something, CallbackInfo ci) {
        if(LODMod.isActive()) {
            Minecraft.getMinecraft().entityRenderer.enableLightmap((double)alpha);
            LODMod.renderer.beforeRenderTerrain(alpha);
            Minecraft.getMinecraft().entityRenderer.disableLightmap((double)alpha);
        }
    }
}
