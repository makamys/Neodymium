package makamys.lodmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.lodmod.renderer.MyRenderer;
import net.minecraft.client.renderer.RenderGlobal;

@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal { 
    
    @Redirect(method = "renderSortedRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderAllRenderLists(ID)V"))
    private void redirectRenderAllRenderLists(RenderGlobal thiz, int p1, double p2) {
        if(MyRenderer.renderWorld) {
            thiz.renderAllRenderLists(p1, p2);
        }
    }
}
