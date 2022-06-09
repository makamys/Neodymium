package makamys.neodymium.mixin.unused.lod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.neodymium.Neodymium;
import makamys.neodymium.renderer.FarChunkCache;
import makamys.neodymium.renderer.NeoRenderer;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Unused remnant from LODMod. Handles reusage of Chunks when a LOD chunk becomes loaded. */
@Mixin(ChunkCache.class)
abstract class MixinChunkCache {
    
    @Redirect(method = "<init>*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunkFromChunkCoords(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk redirectGetChunkFromChunkCoords(World world, int p1, int p2) {
        Chunk chunk = world.getChunkFromChunkCoords(p1, p2);
        if(Neodymium.isActive() && FarChunkCache.class.isInstance(this.getClass()) && chunk.isEmpty()) {
            Chunk myChunk = Neodymium.renderer.getChunkFromChunkCoords(p1, p2);
            if(myChunk != null) {
                chunk = myChunk;
            }
        }
        return chunk;
    }
    
}
