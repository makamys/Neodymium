package makamys.neodymium.ducks;

import java.util.List;

import makamys.neodymium.renderer.ChunkMesh;

public interface IWorldRenderer {
    public List<ChunkMesh> getChunkMeshes();
    public boolean isDrawn();
}
