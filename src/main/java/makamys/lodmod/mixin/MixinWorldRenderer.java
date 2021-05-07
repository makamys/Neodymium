package makamys.lodmod.mixin;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.lodmod.LODMod;
import makamys.lodmod.ducks.ITessellator;
import makamys.lodmod.ducks.IWorldRenderer;
import makamys.lodmod.renderer.ChunkMesh;
import makamys.lodmod.renderer.FarChunkCache;
import makamys.lodmod.renderer.FarWorldRenderer;
import makamys.lodmod.renderer.LODRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

@Mixin(WorldRenderer.class)
abstract class MixinWorldRenderer implements IWorldRenderer {
    
    @Shadow
    public int posX;
    @Shadow
    public int posY;
    @Shadow
    public int posZ;
    
    public List<ChunkMesh> chunkMeshes = new ArrayList<>();
    
    @Redirect(method = "setPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;renderAABB(Lnet/minecraft/util/AxisAlignedBB;)V"))
    private void redirectRenderAABB(AxisAlignedBB p1) {
        if(!FarWorldRenderer.class.isInstance(this.getClass())) {
            RenderItem.renderAABB(p1);
        }
    }
    
    @Redirect(method = "updateRenderer", at = @At(value = "NEW", target = "Lnet/minecraft/world/ChunkCache;"))
    private ChunkCache redirectConstructChunkCache(World p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8) {
        if(!FarWorldRenderer.class.isInstance(this.getClass())) {
            return new ChunkCache(p1, p2, p3, p4, p5, p6, p7, p8);
        } else {
            return new FarChunkCache(p1, p2, p3, p4, p5, p6, p7, p8);
        }
    }
    
    @Inject(method = "updateRenderer", at = @At(value = "HEAD"))
    private void preUpdateRenderer(CallbackInfo ci) {
        if(LODMod.isActive()) {
            chunkMeshes.clear();
        }
    }
    
    @Inject(method = "updateRenderer", at = @At(value = "TAIL"))
    private void postUpdateRenderer(CallbackInfo ci) {
        if(LODMod.isActive()) {
            LODMod.renderer.onWorldRendererPost(WorldRenderer.class.cast(this));
            chunkMeshes.clear();
        }
    }
    
    @Inject(method = "postRenderBlocks", at = @At(value = "HEAD"))
    private void prePostRenderBlocks(CallbackInfo ci) {
        if(LODMod.isActive()) {
            chunkMeshes.add(((ITessellator)Tessellator.instance).toChunkMesh());
        }
    }
    
    @Redirect(method = "postRenderBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"))
    private int redirectPostRenderBlocksDraw() {
        if(!FarWorldRenderer.class.isInstance(this.getClass())) {
            return Tessellator.instance.draw();
        } else {
            Tessellator.instance.reset();
            return 0;
        }
    }
    
    // There's probably a nicer way to do this
    
    @Redirect(method = "postRenderBlocks", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V"))
    private void redirectPostRenderBlocksGL1() {
        if(!FarWorldRenderer.class.isInstance(this.getClass())) {
            GL11.glPopMatrix();
        }
    }
    
    @Redirect(method = "postRenderBlocks", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEndList()V"))
    private void redirectPostRenderBlocksGL2() {
        if(!FarWorldRenderer.class.isInstance(this.getClass())) {
            GL11.glEndList();
        }
    }
    
    // XXX this is inconsistent, Forge callbacks are preserved in postRenderBlocks but not preRenderBlocks
    
    @Inject(method = "preRenderBlocks", at = @At(value = "HEAD"))
    private void preRenderBlocksInjector(CallbackInfo ci) {
        if(FarWorldRenderer.class.isInstance(this.getClass())) {
            Tessellator.instance.setTranslation((double)(-this.posX), (double)(-this.posY), (double)(-this.posZ));
            ci.cancel();
        }
    }
    
    @Inject(method = "setDontDraw", at = @At(value = "HEAD"))
    private void preSetDontDraw(CallbackInfo ci) {
        if(LODMod.isActive()) {
            LODMod.renderer.onDontDraw(WorldRenderer.class.cast(this));
        }
    }
    
    @Override
    public List<ChunkMesh> getChunkMeshes() {
        return chunkMeshes;
    }
}
