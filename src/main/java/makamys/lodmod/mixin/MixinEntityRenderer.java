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
}
