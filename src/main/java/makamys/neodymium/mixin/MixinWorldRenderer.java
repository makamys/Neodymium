package makamys.neodymium.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.NeoRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

/** Inserts hooks in WorldRenderer to listen for changes, and to grab the tessellator data right before rendering. */
@Mixin(WorldRenderer.class)
abstract class MixinWorldRenderer implements IWorldRenderer {
    
    @Shadow
    private boolean isInFrustum;
    @Shadow
    public boolean[] skipRenderPass;
    
    @Shadow
    public boolean needsUpdate;
    
    boolean savedDrawnStatus;
    private boolean insideUpdateRenderer;
    
    public List<ChunkMesh> chunkMeshes;
    
    @Inject(method = "updateRenderer", at = @At(value = "HEAD"))
    private void preUpdateRenderer(CallbackInfo ci) {
        saveDrawnStatus();
        insideUpdateRenderer = true;
        
        if(Neodymium.isActive()) {
            if(needsUpdate) {
                if(chunkMeshes != null) {
                    chunkMeshes.clear();
                } else {
                    chunkMeshes = new ArrayList<>();
                }
            } else {
                chunkMeshes = null;
            }
        }
    }
    
    @Inject(method = "updateRenderer", at = @At(value = "RETURN"))
    private void postUpdateRenderer(CallbackInfo ci) {
        notifyIfDrawnStatusChanged();
        insideUpdateRenderer = false;
        
        if(Neodymium.isActive()) {
            if(chunkMeshes != null) {
                Neodymium.renderer.onWorldRendererPost(WorldRenderer.class.cast(this));
                chunkMeshes.clear();
            }
        }
    }
    
    @Inject(method = "postRenderBlocks", at = @At(value = "HEAD"))
    private void prePostRenderBlocks(int pass, EntityLivingBase entity, CallbackInfo ci) {
        if(insideUpdateRenderer && Neodymium.isActive()) {
            if(chunkMeshes != null) {
                chunkMeshes.add(ChunkMesh.fromTessellator(pass, WorldRenderer.class.cast(this), Tessellator.instance));
            }
        }
    }
    
    @Inject(method = "setDontDraw", at = @At(value = "HEAD"))
    private void preSetDontDraw(CallbackInfo ci) {
        if(Neodymium.isActive()) {
            Neodymium.renderer.onWorldRendererChanged(WorldRenderer.class.cast(this), NeoRenderer.WorldRendererChange.DELETED);
        }
    }
    
    @Override
    public List<ChunkMesh> getChunkMeshes() {
        return chunkMeshes;
    }
    
    @Inject(method = "updateInFrustum", at = @At(value = "HEAD"))
    private void preUpdateInFrustum(CallbackInfo ci) {
        saveDrawnStatus();
    }
    
    @Inject(method = "updateInFrustum", at = @At(value = "RETURN"))
    private void postUpdateInFrustum(CallbackInfo ci) {
        notifyIfDrawnStatusChanged();
    }
    
    private void saveDrawnStatus() {
        savedDrawnStatus = isDrawn();
    }
    
    private void notifyIfDrawnStatusChanged() {
        boolean drawn = isDrawn();
        if(Neodymium.isActive() && drawn != savedDrawnStatus) {
            Neodymium.renderer.onWorldRendererChanged(WorldRenderer.class.cast(this), drawn ? NeoRenderer.WorldRendererChange.VISIBLE : NeoRenderer.WorldRendererChange.INVISIBLE);
        }
    }
    
    @Override
    public boolean isDrawn() {
        return isInFrustum && (!skipRenderPass[0] || !skipRenderPass[1]);
    }
}
