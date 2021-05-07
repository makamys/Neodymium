package makamys.lodmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.lodmod.LODMod;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;

@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal { 
    
    @Redirect(method = "renderSortedRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderAllRenderLists(ID)V"))
    private void redirectRenderAllRenderLists(RenderGlobal thiz, int p1, double p2) {
        if(LODMod.isActive() && LODMod.renderer.renderWorld) {
            thiz.renderAllRenderLists(p1, p2);
        }
    }
    
    @Redirect(method = "renderSortedRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;getGLCallListForPass(I)I"))
    public int redirectCallList(WorldRenderer thiz, int arg) {
        int numba = thiz.getGLCallListForPass(arg);
        if(numba != -1) {
            LODMod.renderer.onWorldRendererFrustumChange(thiz, true);
        }
        return numba;
    }
}
