package makamys.neodymium.renderer.compat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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

    @Override
    public void readMeshQuad(MeshQuad meshQuad, int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        //No RPLE or Shaders
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            meshQuad.xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            meshQuad.ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            meshQuad.zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            meshQuad.us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            meshQuad.vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            meshQuad.cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            // TODO normals?

            meshQuad.bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;
        }

        if(vertices == 3) {
            // Quadrangulate!
            meshQuad.xs[3] = meshQuad.xs[2];
            meshQuad.ys[3] = meshQuad.ys[2];
            meshQuad.zs[3] = meshQuad.zs[2];

            meshQuad.us[3] = meshQuad.us[2];
            meshQuad.vs[3] = meshQuad.vs[2];

            meshQuad.cs[3] = meshQuad.cs[2];

            meshQuad.bs[3] = meshQuad.bs[2];
        }
    }

    @Override
    public void writeMeshQuadToBuffer(MeshQuad meshQuad, BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(meshQuad.xs[vi]);
            out.writeFloat(meshQuad.ys[vi]);
            out.writeFloat(meshQuad.zs[vi]);

            float u = meshQuad.us[vi];
            float v = meshQuad.vs[vi];

            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            out.writeInt(meshQuad.cs[vi]);

            out.writeInt(meshQuad.bs[vi]);

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
