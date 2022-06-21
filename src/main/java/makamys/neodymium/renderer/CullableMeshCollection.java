package makamys.neodymium.renderer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import makamys.neodymium.Config;
import makamys.neodymium.renderer.ChunkMesh.Flags;

public class CullableMeshCollection {
    
    private ChunkMesh[] meshes = new ChunkMesh[QuadNormal.values().length];
    
    public CullableMeshCollection(int x, int y, int z, Flags flags, int quadCount, List<MeshQuad> quads, int pass) {
        if(Config.cullFaces) {
            for(QuadNormal normal : QuadNormal.values()) {
                List<MeshQuad> normalQuads = quads.stream().filter(q -> MeshQuad.isValid(q) && q.normal == normal).collect(Collectors.toList());
                if(!normalQuads.isEmpty()) {
                    putMeshWithNormal(normal, new ChunkMesh(x, y, z, flags, normalQuads.size(), normalQuads, pass));
                    getMeshWithNormal(normal).normal = normal;
                }
            }
        } else {
            putMeshWithNormal(QuadNormal.NONE, new ChunkMesh(x, y, z, flags, quadCount, quads, pass));
        }
    }

    public ChunkMesh getMeshWithNormal(QuadNormal normal) {
        return meshes[normal.ordinal()];
    }
    
    public void putMeshWithNormal(QuadNormal normal, ChunkMesh mesh) {
        meshes[normal.ordinal()] = mesh;
    }
    
    public List<ChunkMesh> getMeshes() {
        return Arrays.asList(meshes);
    }

    public void destroy() {
        for(ChunkMesh mesh : meshes) {
            if(mesh != null) mesh.destroy();
        }
    }

    public boolean isVisible() {
        for(ChunkMesh mesh : meshes) {
            if(mesh != null && mesh.visible) return true;
        }
        return false;
    }
    
}
