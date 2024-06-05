package makamys.neodymium.ducks;

import java.util.List;

import makamys.neodymium.renderer.ChunkMesh;

public interface NeodymiumWorldRenderer {
    List<ChunkMesh> nd$getChunkMeshes();
    ChunkMesh nd$beginRenderPass(int pass);
    void nd$endRenderPass(ChunkMesh mesh);
    boolean nd$isDrawn();
    void nd$suppressRenderPasses(boolean suppressed);
}
