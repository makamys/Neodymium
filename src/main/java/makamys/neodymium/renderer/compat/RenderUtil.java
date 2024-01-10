package makamys.neodymium.renderer.compat;

import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.NeoRenderer;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;

public interface RenderUtil {
    int QUAD_OFFSET_XPOS = 0;
    int QUAD_OFFSET_YPOS = 1;
    int QUAD_OFFSET_ZPOS = 2;

    void readMeshQuad(int[] tessBuffer, int tessOffset, int[] quadBuffer, int quadOffset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags);

    /**
     * @implSpec These needs to be kept in sync with the attributes in {@link NeoRenderer#init()}
     */
    void writeMeshQuadToBuffer(int[] meshQuadBuffer, int quadOffset, BufferWriter out, int expectedStride);

    int vertexSizeInTessellator();

    int vertexSizeInQuadBuffer();

    // Include the quad normal
    default int quadSize() {
        return vertexSizeInQuadBuffer() * 4 + 1;
    }

    void initVertexAttributes(AttributeSet attributes);

    default void applyVertexAttributes(AttributeSet attributes) {
        attributes.enable();
    }
}
