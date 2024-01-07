package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.ITessellator;
import makamys.neodymium.renderer.ChunkMesh;
import net.minecraft.client.renderer.Tessellator;

@Mixin(Tessellator.class)
abstract class MixinTessellator implements ITessellator {
    
    private boolean nd$captureMeshes;
    
    @Shadow protected abstract void reset();

    @Shadow private boolean isDrawing;
    
    @Inject(method = "draw",
            at = @At(value = "HEAD"),
            cancellable = true,
            require = 1)
    private void preDraw(CallbackInfoReturnable<Integer> cir) {
        if(nd$captureMeshes) {
            ChunkMesh.preTessellatorDraw((Tessellator)(Object)this);
            
            if(Config.enabled && !Config.enableVanillaChunkMeshes) {
                isDrawing = false;
                reset();
                cir.setReturnValue(0);
            }
        }
    }
    
    @Override
    public void enableMeshCapturing(boolean enable) {
        nd$captureMeshes = enable;
    }
    
}
