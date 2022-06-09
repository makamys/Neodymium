package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import makamys.neodymium.LODMod;
import makamys.neodymium.renderer.FarChunkCache;
import makamys.neodymium.renderer.LODRenderer;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

@Mixin(ChunkCache.class)
abstract class MixinChunkCache {
    
    @Redirect(method = "<init>*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunkFromChunkCoords(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk redirectGetChunkFromChunkCoords(World world, int p1, int p2) {
        Chunk chunk = world.getChunkFromChunkCoords(p1, p2);
        if(LODMod.isActive() && FarChunkCache.class.isInstance(this.getClass()) && chunk.isEmpty()) {
            Chunk myChunk = LODMod.renderer.getChunkFromChunkCoords(p1, p2);
            if(myChunk != null) {
                chunk = myChunk;
            }
        }
        return chunk;
    }
    
}
