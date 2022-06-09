package makamys.neodymium.ducks;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import makamys.neodymium.renderer.ChunkMesh;
import net.minecraft.client.renderer.WorldRenderer;

public interface IWorldRenderer {
    public List<ChunkMesh> getChunkMeshes();
    public boolean isDrawn();
}
