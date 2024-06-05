package makamys.neodymium.renderer.compat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import makamys.neodymium.config.Config;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.MeshQuad;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;
import org.lwjgl.opengl.GL11;

import static makamys.neodymium.renderer.MeshQuad.DEFAULT_BRIGHTNESS;
import static makamys.neodymium.renderer.MeshQuad.DEFAULT_COLOR;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RenderUtilVanilla implements RenderUtil {
    public static final RenderUtilVanilla INSTANCE = new RenderUtilVanilla();

    public static final int QUAD_OFFSET_U = 3;
    public static final int QUAD_OFFSET_V = 4;
    public static final int QUAD_OFFSET_C = 5;
    public static final int QUAD_OFFSET_B = 6;

    @Override
    public void readMeshQuad(int[] tessBuffer, int tessOffset, int[] quadBuffer, int quadOffset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        val tessVertexSize = vertexSizeInTessellator();
        val quadVertexSize = vertexSizeInQuadBuffer();

        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int tI = tessOffset + vi * tessVertexSize;
            int qI = quadOffset + vi * quadVertexSize;

            quadBuffer[qI + QUAD_OFFSET_XPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI]) + offsetX);
            quadBuffer[qI + QUAD_OFFSET_YPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI + 1]) + offsetY);
            quadBuffer[qI + QUAD_OFFSET_ZPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI + 2]) + offsetZ);

            quadBuffer[qI + QUAD_OFFSET_U] = tessBuffer[tI + 3];
            quadBuffer[qI + QUAD_OFFSET_V] = tessBuffer[tI + 4];

            quadBuffer[qI + QUAD_OFFSET_C] = flags.hasColor ? tessBuffer[tI + 5] : DEFAULT_COLOR;

            // TODO normals?

            quadBuffer[qI + QUAD_OFFSET_B] = flags.hasBrightness ? tessBuffer[tI + 7] : DEFAULT_BRIGHTNESS;
        }


        if(vertices == 3) {
            // Quadrangulate!
            int q2 = quadOffset + 2 * quadVertexSize;
            int q3 = quadOffset + 3 * quadVertexSize;

            System.arraycopy(quadBuffer, q2, quadBuffer, q3, quadVertexSize);
        }
    }

    @Override
    public int vertexSizeInTessellator() {
        // pos + uv + color + normal + brightness
        return 3 + 2 + 1 + 1 + 1;
    }

    @Override
    public int vertexSizeInQuadBuffer() {
        // pos + uv + color + brightness;
        return 3 + 2 + 1 + 1;
    }

    @Override
    public void writeMeshQuadToBuffer(int[] meshQuadBuffer, int quadOffset, BufferWriter out, int expectedStride) {
        val vertexSize = vertexSizeInQuadBuffer();
        for(int vi = 0; vi < 4; vi++) {
            int offset = quadOffset + vi * vertexSize;
            out.writeFloat(Float.intBitsToFloat(meshQuadBuffer[offset + QUAD_OFFSET_XPOS]));
            out.writeFloat(Float.intBitsToFloat(meshQuadBuffer[offset + QUAD_OFFSET_YPOS]));
            out.writeFloat(Float.intBitsToFloat(meshQuadBuffer[offset + QUAD_OFFSET_ZPOS]));

            float u = Float.intBitsToFloat(meshQuadBuffer[offset + QUAD_OFFSET_U]);
            float v = Float.intBitsToFloat(meshQuadBuffer[offset + QUAD_OFFSET_V]);

            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            out.writeInt(meshQuadBuffer[offset + QUAD_OFFSET_C]);

            out.writeInt(meshQuadBuffer[offset + QUAD_OFFSET_B]);

            assert out.position() % expectedStride == 0;
        }
    }

    @Override
    public void initVertexAttributes(AttributeSet attributes) {
        attributes.addAttribute("POS", 3, 4, GL_FLOAT);
        if (Config.shortUV) {
            attributes.addAttribute("TEXTURE", 2, 2, GL_UNSIGNED_SHORT);
        } else {
            attributes.addAttribute("TEXTURE", 2, 4, GL_FLOAT);
        }
        attributes.addAttribute("COLOR", 4, 1, GL_UNSIGNED_BYTE);
        attributes.addAttribute("BRIGHTNESS", 2, 2, GL_SHORT);
    }
}
