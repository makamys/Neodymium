package makamys.lodmod.ducks;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import makamys.lodmod.renderer.ChunkMesh;
import net.minecraft.client.renderer.WorldRenderer;

public interface IWorldRenderer {
    public List<ChunkMesh> getChunkMeshes();
    public void myTick();
}
