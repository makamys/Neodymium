package makamys.lodmod.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.lodmod.LODMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer {
    
    @Shadow
    private float farPlaneDistance;
    
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", shift = At.Shift.AFTER, ordinal = 1))
    private void onConstructed(CallbackInfo ci) {
        if(LODMod.isActive()) {
            farPlaneDistance *= LODMod.renderer.getFarPlaneDistanceMultiplier();
        }
    }
    
    @Inject(method = "setupFog", at = @At(value = "RETURN"))
    private void afterSetupFog(int mode, float alpha, CallbackInfo ci) {
        if(LODMod.isActive()) {
            LODMod.renderer.afterSetupFog(mode, alpha, farPlaneDistance);
        }
    }
}
