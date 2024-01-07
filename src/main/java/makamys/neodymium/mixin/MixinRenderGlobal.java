package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import makamys.neodymium.Neodymium;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

/** Blocks vanilla chunk rendering while NeoRenderer is active. */
@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal { 
    
    @Shadow
    private WorldRenderer[] sortedWorldRenderers;
    
    private boolean nd$isInsideUpdateRenderers;
    
    @Inject(method = "renderAllRenderLists", at = @At(value = "HEAD"), cancellable = true)
    private void blockVanillaChunkRendering(int p1, double p2, CallbackInfo ci) {
        if(!Neodymium.shouldRenderVanillaWorld()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "renderSortedRenderers",
            at = @At(value = "HEAD"),
            cancellable = true,
            require = 1)
    public void preRenderSortedRenderers(int startRenderer, int numRenderers, int renderPass, double partialTickTime, CallbackInfoReturnable<Integer> cir) {
        if(Neodymium.isActive()) {
            cir.setReturnValue(Neodymium.renderer.preRenderSortedRenderers(renderPass, partialTickTime, sortedWorldRenderers));
        }
    }
    
    @Inject(method = "loadRenderers", at = @At(value = "HEAD"))
    public void preLoadRenderers(CallbackInfo ci) {
        Neodymium.destroyRenderer();
    }
    
    @Inject(method = "updateRenderers", at = @At(value = "RETURN"))
    public void speedUpChunkUpdatesForDebug(EntityLivingBase entity, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        if(Neodymium.isActive() && !nd$isInsideUpdateRenderers) {
            nd$isInsideUpdateRenderers = true;
            for(int i = 0; i < Neodymium.renderer.rendererSpeedup; i++) {
                ((RenderGlobal)(Object)this).updateRenderers(entity, flag);
            }
            nd$isInsideUpdateRenderers = false;
        }
    }
}
