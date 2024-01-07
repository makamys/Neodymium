package makamys.neodymium.mixin;

import com.google.common.collect.Lists;
import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.ITessellator;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.NeoRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/** Inserts hooks in WorldRenderer to listen for changes, and to grab the tessellator data right before rendering. */
@Mixin(WorldRenderer.class)
abstract class MixinWorldRenderer implements IWorldRenderer {
    
    @Shadow
    private boolean isInFrustum;
    @Shadow
    public boolean[] skipRenderPass;
    
    @Shadow
    public boolean needsUpdate;
    
    private boolean nd$savedDrawnStatus;
    
    private List<ChunkMesh> nd$chunkMeshes;
    
    @Inject(method = {"updateRenderer"},
            at = @At(value = "HEAD"),
            require = 1)
    private void preUpdateRenderer(CallbackInfo ci) {
        preUpdateRenderer(false);
    }
    
    @Inject(method = {"updateRendererSort"},
            at = @At(value = "HEAD"),
            require = 1)
    private void preUpdateRendererSort(CallbackInfo ci) {
        preUpdateRenderer(true);
    }
    
    @Unique
    private void preUpdateRenderer(boolean sort) {
        saveDrawnStatus();
        
        if(Neodymium.isActive()) {
            if(nd$chunkMeshes != null) {
                Collections.fill(nd$chunkMeshes, null);
            } else {
                nd$chunkMeshes = Lists.newArrayList(null, null);
            }
        }
    }
    
    @Inject(method = {"updateRenderer"},
            at = @At(value = "RETURN"),
            require = 1)
    private void postUpdateRenderer(CallbackInfo ci) {
        postUpdateRenderer(false);
    }
    
    @Inject(method = {"updateRendererSort"},
            at = @At(value = "RETURN"),
            require = 1)
    private void postUpdateRendererSort(CallbackInfo ci) {
        postUpdateRenderer(true);
    }
    
    @Unique
    private void postUpdateRenderer(boolean sort) {
        notifyIfDrawnStatusChanged();
        
        if(Neodymium.isActive()) {
            if(nd$chunkMeshes != null) {
                Neodymium.renderer.onWorldRendererPost(WorldRenderer.class.cast(this), sort);
                Collections.fill(nd$chunkMeshes, null);
            }
        }
    }
    
    @Inject(method = "preRenderBlocks",
            at = @At("HEAD"),
            require = 1)
    private void prePreRenderBlocks(int pass, CallbackInfo ci) {
        if(Neodymium.isActive()) {
            ((ITessellator)Tessellator.instance).enableMeshCapturing(true);
            ChunkMesh cm = new ChunkMesh((WorldRenderer)(Object)this, pass);
            nd$chunkMeshes.set(pass, cm);
            ChunkMesh.setCaptureTarget(cm);
        }
    }

    @Redirect(method = "preRenderBlocks",
              at = @At(value = "INVOKE",
                       target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"),
              require = 1)
    private void noNewList(int list, int mode) {
        if (!Neodymium.isActive()) {
            GL11.glNewList(list, mode);
        }
    }

    @Redirect(method = "postRenderBlocks",
              at = @At(value = "INVOKE",
                       target = "Lorg/lwjgl/opengl/GL11;glEndList()V"),
              require = 1)
    private void noEndList() {
        if (!Neodymium.isActive())
            GL11.glEndList();
    }
    
    @Inject(method = "postRenderBlocks",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"),
            require = 1)
    private void prePostRenderBlocks(int pass, EntityLivingBase entity, CallbackInfo ci) {
        /*if(Neodymium.isActive()) {
            if(nd$chunkMeshes != null) {
                if(nd$chunkMeshes.get(pass) == null) {
                    nd$chunkMeshes.set(pass, ChunkMesh.fromTessellator(pass, WorldRenderer.class.cast(this)));
                }
                nd$chunkMeshes.get(pass).addTessellatorData(Tessellator.instance);
            }
        }*/
    }
    
    @Inject(method = "postRenderBlocks",
            at = @At("RETURN"),
            require = 1)
    private void postPostRenderBlocks(int pass, EntityLivingBase entity, CallbackInfo ci) {
        if(Neodymium.isActive()) {
            nd$chunkMeshes.get(pass).finishConstruction();
            ((ITessellator)Tessellator.instance).enableMeshCapturing(false);
            ChunkMesh.setCaptureTarget(null);
        }
    }
    
    @Inject(method = "setDontDraw",
            at = @At(value = "HEAD"),
            require = 1)
    private void preSetDontDraw(CallbackInfo ci) {
        if(Neodymium.isActive()) {
            Neodymium.renderer.onWorldRendererChanged(WorldRenderer.class.cast(this), NeoRenderer.WorldRendererChange.DELETED);
        }
    }
    
    @Override
    public List<ChunkMesh> getChunkMeshes() {
        return nd$chunkMeshes;
    }
    
    @Inject(method = "updateInFrustum",
            at = @At(value = "HEAD"),
            require = 1)
    private void preUpdateInFrustum(CallbackInfo ci) {
        saveDrawnStatus();
    }
    
    @Inject(method = "updateInFrustum",
            at = @At(value = "RETURN"),
            require = 1)
    private void postUpdateInFrustum(CallbackInfo ci) {
        notifyIfDrawnStatusChanged();
    }
    
    @Unique
    private void saveDrawnStatus() {
        nd$savedDrawnStatus = isDrawn();
    }
    
    @Unique
    private void notifyIfDrawnStatusChanged() {
        boolean drawn = isDrawn();
        if(Neodymium.isActive() && drawn != nd$savedDrawnStatus) {
            Neodymium.renderer.onWorldRendererChanged(WorldRenderer.class.cast(this), drawn ? NeoRenderer.WorldRendererChange.VISIBLE : NeoRenderer.WorldRendererChange.INVISIBLE);
        }
    }
    
    @Override
    public boolean isDrawn() {
        if (!(skipRenderPass[0] && skipRenderPass[1]))
            return true;
        if (!Compat.isShaders())
            return isInFrustum;
        return true;
    }
}
