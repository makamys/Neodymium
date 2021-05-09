package makamys.lodmod.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.lodmod.LODMod;
import makamys.lodmod.renderer.LODRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer {
    
    @Shadow
    private float farPlaneDistance;
    
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", shift = At.Shift.AFTER, args = "log=true", ordinal = 0))
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
    
    @Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V"))
    private void afterSetupFog(int pname, float param, int mode, float alpha) {
        if(LODMod.isActive()) {
            EntityLivingBase var3 = Minecraft.getMinecraft().renderViewEntity;
            if(pname == GL11.GL_FOG_START && mode != 999 && mode != -1 && !var3.isPotionActive(Potion.blindness) && !Minecraft.getMinecraft().theWorld.provider.doesXZShowFog((int)var3.posX, (int)var3.posZ)) {
                GL11.glFogf(pname, farPlaneDistance * 0.2f);
            } else {
                GL11.glFogf(pname, param);
            }
        }
    }
}
