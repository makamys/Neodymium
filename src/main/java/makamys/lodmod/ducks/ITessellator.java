package makamys.lodmod.ducks;

import org.spongepowered.asm.mixin.Mixin;

import makamys.lodmod.renderer.ChunkMesh;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;

public interface ITessellator {
    public ChunkMesh toChunkMesh(int pass, WorldRenderer wr);
}
