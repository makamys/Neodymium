package makamys.neodymium.mixin;

import com.google.common.collect.Lists;
import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.NeodymiumTessellator;
import makamys.neodymium.ducks.NeodymiumWorldRenderer;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.NeoRenderer;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/** Inserts hooks in WorldRenderer to listen for changes, and to grab the tessellator data right before rendering. */
@Mixin(WorldRenderer.class)
abstract class MixinWorldRenderer implements NeodymiumWorldRenderer {
    // ---additions---

    @Unique
    private boolean nd$savedDrawnStatus;
    @Unique
    private List<ChunkMesh> nd$chunkMeshes;
    @Unique
    private boolean nd$renderPassSuppressed;

    @Override
    public void nd$suppressRenderPasses(boolean suppressed) {
        this.nd$renderPassSuppressed = suppressed;
    }

    @Override
    public List<ChunkMesh> nd$getChunkMeshes() {
        return nd$chunkMeshes;
    }

    @Override
    public ChunkMesh nd$beginRenderPass(int pass) {
        if(Neodymium.isActive() && !nd$renderPassSuppressed) {
            ChunkMesh cm = new ChunkMesh((WorldRenderer)(Object)this, pass);
            ((NeodymiumTessellator)Compat.tessellator()).nd$setCaptureTarget(cm);
            return cm;
        }
        return null;
    }

    @Override
    public void nd$endRenderPass(ChunkMesh chunkMesh) {
        if(Neodymium.isActive() && !nd$renderPassSuppressed) {
            if (chunkMesh != null) {
                chunkMesh.finishConstruction();
            }
            ((NeodymiumTessellator)Compat.tessellator()).nd$setCaptureTarget(null);
        }
    }

    @Override
    public boolean nd$isDrawn() {
        return !(skipRenderPass[0] && skipRenderPass[1]);
    }

    @Unique
    private void nd$reset(boolean sort) {
        nd$saveDrawnStatus();

        if(Neodymium.isActive()) {
            if(nd$chunkMeshes != null) {
                Collections.fill(nd$chunkMeshes, null);
            } else {
                nd$chunkMeshes = Lists.newArrayList(null, null);
            }
        }
    }

    @Unique
    private void nd$postUpdateRenderer(boolean sort) {
        nd$notifyIfDrawnStatusChanged();

        if(Neodymium.isActive()) {
            if(nd$chunkMeshes != null) {
                Neodymium.renderer.onWorldRendererPost((WorldRenderer) (Object) this, sort);
                Collections.fill(nd$chunkMeshes, null);
            }
        }
    }

    @Unique
    private void nd$saveDrawnStatus() {
        nd$savedDrawnStatus = nd$isDrawn();
    }

    @Unique
    private void nd$notifyIfDrawnStatusChanged() {
        boolean drawn = nd$isDrawn();
        if(Neodymium.isActive() && drawn != nd$savedDrawnStatus) {
            Neodymium.renderer.onWorldRendererChanged((WorldRenderer) (Object) this, drawn ? NeoRenderer.WorldRendererChange.VISIBLE : NeoRenderer.WorldRendererChange.INVISIBLE);
        }
    }

    // ---mixins---

    @Shadow
    public boolean isInFrustum;
    @Shadow
    public boolean[] skipRenderPass;

    // Inject before first instruction inside if(needsUpdate) block
    @Inject(method = {"updateRenderer"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/WorldRenderer;needsUpdate:Z", ordinal = 0),
            require = 1)
    private void preUpdateRenderer(CallbackInfo ci) {
        nd$reset(false);
    }

    @Inject(method = {"updateRendererSort"},
            at = @At(value = "HEAD"),
            require = 1)
    private void preUpdateRendererSort(CallbackInfo ci) {
        nd$reset(true);
    }
    
    // Inject after last instruction inside if(needsUpdate) block
    @Inject(method = {"updateRenderer"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/WorldRenderer;isInitialized:Z", ordinal = 0, shift = Shift.AFTER),
            require = 1)
    private void postUpdateRenderer(CallbackInfo ci) {
        nd$postUpdateRenderer(false);
    }

    @Inject(method = {"updateRendererSort"},
            at = @At(value = "RETURN"),
            require = 1)
    private void postUpdateRendererSort(CallbackInfo ci) {
        nd$postUpdateRenderer(true);
    }

    @Inject(method = "preRenderBlocks",
            at = @At("HEAD"),
            require = 1)
    private void prePreRenderBlocks(int pass, CallbackInfo ci) {
        nd$chunkMeshes.set(pass, nd$beginRenderPass(pass));
    }

    @Redirect(method = "preRenderBlocks",
              at = @At(value = "INVOKE",
                       target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"),
              require = 1)
    private void noNewList(int list, int mode) {
        if (!Neodymium.isActive() || Compat.keepRenderListLogic()) {
            GL11.glNewList(list, mode);
        }
    }

    @Redirect(method = "postRenderBlocks",
              at = @At(value = "INVOKE",
                       target = "Lorg/lwjgl/opengl/GL11;glEndList()V"),
              require = 1)
    private void noEndList() {
        if (!Neodymium.isActive() || Compat.keepRenderListLogic())
            GL11.glEndList();
    }

    @Inject(method = "postRenderBlocks",
            at = @At("RETURN"),
            require = 1)
    private void postPostRenderBlocks(int pass, EntityLivingBase entity, CallbackInfo ci) {
        nd$endRenderPass(nd$chunkMeshes.get(pass));
    }

    @Inject(method = "setDontDraw",
            at = @At(value = "HEAD"),
            require = 1)
    private void preSetDontDraw(CallbackInfo ci) {
        if(Neodymium.isActive()) {
            Neodymium.renderer.onWorldRendererChanged((WorldRenderer) (Object) this, NeoRenderer.WorldRendererChange.DELETED);
        }
    }

    @Inject(method = "updateInFrustum",
            at = @At(value = "HEAD"),
            require = 1)
    private void preUpdateInFrustum(CallbackInfo ci) {
        nd$saveDrawnStatus();
    }

    @Inject(method = "updateInFrustum",
            at = @At(value = "RETURN"),
            require = 1)
    private void postUpdateInFrustum(CallbackInfo ci) {
        nd$notifyIfDrawnStatusChanged();
    }
}
