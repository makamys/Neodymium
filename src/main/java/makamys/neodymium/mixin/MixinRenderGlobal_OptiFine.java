package makamys.neodymium.mixin;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.neodymium.Neodymium;
import net.minecraft.client.renderer.RenderGlobal;

/** Blocks vanilla chunk rendering while NeoRenderer is active. (OptiFine compat) */
@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal_OptiFine {
    
    // for OptiFine's Fast Render option
    @Redirect(method = "renderSortedRenderersFast", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallLists(Ljava/nio/IntBuffer;)V"), remap=false)
    private void redirectRenderAllRenderLists(IntBuffer buffer) {
        if(Neodymium.shouldRenderVanillaWorld()) {
            GL11.glCallLists(buffer);
        }
    }
    
}
