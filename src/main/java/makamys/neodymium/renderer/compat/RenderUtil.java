package makamys.neodymium.renderer.compat;

import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.MeshQuad;
import makamys.neodymium.renderer.NeoRenderer;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;

public interface RenderUtil {
    void readMeshQuad(MeshQuad meshQuad, int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags);

    /**
     * @implSpec These needs to be kept in sync with the attributes in {@link NeoRenderer#init()}
     */
    void writeMeshQuadToBuffer(MeshQuad meshQuad, BufferWriter out, int expectedStride);

    void initVertexAttributes(AttributeSet attributes);
}
