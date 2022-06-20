package makamys.neodymium.ducks;

import java.util.List;

import makamys.neodymium.renderer.CullableMeshCollection;

public interface IWorldRenderer {
    public List<CullableMeshCollection> getChunkMeshes();
    public boolean isDrawn();
}
