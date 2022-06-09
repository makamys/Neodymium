package makamys.neodymium.mixin;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.neodymium.LODMod;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderGlobal;

@Mixin(RenderGlobal.class)
abstract class MixinRenderGlobal_OptiFine {
    
    // for OptiFine's Fast Render option
    @Redirect(method = "renderSortedRenderersFast", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallLists(Ljava/nio/IntBuffer;)V"), remap=false)
    private void redirectRenderAllRenderLists(IntBuffer buffer) {
        if(LODMod.shouldRenderVanillaWorld()) {
            GL11.glCallLists(buffer);
        }
    }
    
}
