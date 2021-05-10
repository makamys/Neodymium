package makamys.lodmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import makamys.lodmod.LODMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;

@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal { 
    
    @Shadow
    private WorldRenderer[] sortedWorldRenderers;
    
    @Redirect(method = "renderSortedRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderAllRenderLists(ID)V"))
    private void redirectRenderAllRenderLists(RenderGlobal thiz, int p1, double p2) {
        if(!LODMod.isActive() || (LODMod.isActive() && LODMod.renderer.renderWorld)) {
            thiz.renderAllRenderLists(p1, p2);
        }
    }
    
    @Inject(method = "renderSortedRenderers", at = @At(value = "HEAD"))
    public void preRenderSortedRenderers(int startRenderer, int numRenderers, int renderPass, double partialTickTime, CallbackInfoReturnable cir) {
        if(LODMod.isActive()) {
            LODMod.renderer.preRenderSortedRenderers(renderPass, partialTickTime, sortedWorldRenderers);
        }
    }
}
