package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import makamys.neodymium.Neodymium;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;

/** Blocks vanilla chunk rendering while NeoRenderer is active. */
@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal { 
    
    @Shadow
    private WorldRenderer[] sortedWorldRenderers;
    
    @Redirect(method = "renderSortedRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderAllRenderLists(ID)V"))
    private void redirectRenderAllRenderLists(RenderGlobal thiz, int p1, double p2) {
        if(Neodymium.shouldRenderVanillaWorld()) {
            thiz.renderAllRenderLists(p1, p2);
        }
    }
    
    @Inject(method = "renderSortedRenderers", at = @At(value = "HEAD"))
    public void preRenderSortedRenderers(int startRenderer, int numRenderers, int renderPass, double partialTickTime, CallbackInfoReturnable cir) {
        if(Neodymium.isActive()) {
            Neodymium.renderer.preRenderSortedRenderers(renderPass, partialTickTime, sortedWorldRenderers);
        }
    }
    
    @Inject(method = "loadRenderers", at = @At(value = "HEAD"))
    public void preLoadRenderers(CallbackInfo ci) {
        if(Neodymium.isActive()) {
            Neodymium.renderer.destroy();
            Neodymium.renderer = null;
        }
    }
}
