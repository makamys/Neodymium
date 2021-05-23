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
    
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", shift = At.Shift.AFTER, ordinal = 0))
    private void onConstructed(CallbackInfo ci) {
        if(LODMod.isActive()) {
            farPlaneDistance *= LODMod.renderer.getFarPlaneDistanceMultiplier();
        }
    }
    
    @Inject(method = "setupFog", at = @At(value = "RETURN"))
    private void afterSetupFog(int mode, float alpha, CallbackInfo ci) {
        if(LODMod.isActive()) {
            EntityLivingBase entity = Minecraft.getMinecraft().renderViewEntity;
            if(LODMod.fogEventWasPosted && mode >= 0 && !Minecraft.getMinecraft().theWorld.provider.doesXZShowFog((int)entity.posX, (int)entity.posZ)) {
                GL11.glFogf(GL11.GL_FOG_START, farPlaneDistance * 0.2f);
                GL11.glFogf(GL11.GL_FOG_END, farPlaneDistance);
            }
        }
    }
}
