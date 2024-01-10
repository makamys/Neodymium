package makamys.neodymium.ducks;

import makamys.neodymium.renderer.ChunkMesh;

public interface NeodymiumTessellator {
    ChunkMesh nd$getCaptureTarget();
    void nd$setCaptureTarget(ChunkMesh target);
    boolean nd$captureData();
}
