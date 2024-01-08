package makamys.neodymium.renderer.compat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RenderUtilShaders implements RenderUtil {
    public static final RenderUtilShaders INSTANCE = new RenderUtilShaders();

    @Override
    public void readMeshQuad(MeshQuad meshQuad, int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for (int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            meshQuad.xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            meshQuad.ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            meshQuad.zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            meshQuad.us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            meshQuad.vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            meshQuad.cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            meshQuad.bs[vi] = flags.hasBrightness ? rawBuffer[i + 6] : DEFAULT_BRIGHTNESS;
            meshQuad.e1[vi] = rawBuffer[i + 7];
            meshQuad.e2[vi] = rawBuffer[i + 8];
            meshQuad.xn[vi] = Float.intBitsToFloat(rawBuffer[i + 9]);
            meshQuad.yn[vi] = Float.intBitsToFloat(rawBuffer[i + 10]);
            meshQuad.zn[vi] = Float.intBitsToFloat(rawBuffer[i + 11]);
            meshQuad.xt[vi] = Float.intBitsToFloat(rawBuffer[i + 12]);
            meshQuad.yt[vi] = Float.intBitsToFloat(rawBuffer[i + 13]);
            meshQuad.zt[vi] = Float.intBitsToFloat(rawBuffer[i + 14]);
            meshQuad.wt[vi] = Float.intBitsToFloat(rawBuffer[i + 15]);
            meshQuad.um[vi] = Float.intBitsToFloat(rawBuffer[i + 16]);
            meshQuad.vm[vi] = Float.intBitsToFloat(rawBuffer[i + 17]);
        }

        if (vertices == 3) {
            // Quadrangulate!
            meshQuad.xs[3] = meshQuad.xs[2];
            meshQuad.ys[3] = meshQuad.ys[2];
            meshQuad.zs[3] = meshQuad.zs[2];

            meshQuad.us[3] = meshQuad.us[2];
            meshQuad.vs[3] = meshQuad.vs[2];

            meshQuad.cs[3] = meshQuad.cs[2];

            meshQuad.bs[3] = meshQuad.bs[2];

            meshQuad.e1[3] = meshQuad.e1[2];
            meshQuad.e2[3] = meshQuad.e2[2];

            meshQuad.xn[3] = meshQuad.xn[2];
            meshQuad.yn[3] = meshQuad.yn[2];
            meshQuad.zn[3] = meshQuad.zn[2];

            meshQuad.xt[3] = meshQuad.xt[2];
            meshQuad.yt[3] = meshQuad.yt[2];
            meshQuad.zt[3] = meshQuad.zt[2];
            meshQuad.wt[3] = meshQuad.wt[2];

            meshQuad.um[3] = meshQuad.um[2];
            meshQuad.vm[3] = meshQuad.vm[2];
        }
    }

    @Override
    public void writeMeshQuadToBuffer(MeshQuad meshQuad, BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(meshQuad.xs[vi]);
            out.writeFloat(meshQuad.ys[vi]);
            out.writeFloat(meshQuad.zs[vi]);

            out.writeFloat(meshQuad.us[vi]);
            out.writeFloat(meshQuad.vs[vi]);

            out.writeInt(meshQuad.cs[vi]);

            out.writeInt(meshQuad.bs[vi]);

            out.writeInt(meshQuad.e1[vi]);
            out.writeInt(meshQuad.e2[vi]);

            out.writeFloat(meshQuad.xn[vi]);
            out.writeFloat(meshQuad.yn[vi]);
            out.writeFloat(meshQuad.zn[vi]);

            out.writeFloat(meshQuad.xt[vi]);
            out.writeFloat(meshQuad.yt[vi]);
            out.writeFloat(meshQuad.zt[vi]);
            out.writeFloat(meshQuad.wt[vi]);

            out.writeFloat(meshQuad.um[vi]);
            out.writeFloat(meshQuad.vm[vi]);

            assert out.position() % expectedStride == 0;
        }
    }

    @Override
    public void initVertexAttributes(AttributeSet attributes) {
        attributes.addAttribute("POS", 3, 4, GL_FLOAT);
        attributes.addAttribute("TEXTURE", 2, 4, GL_FLOAT);
        attributes.addAttribute("COLOR", 4, 1, GL_UNSIGNED_BYTE);
        attributes.addAttribute("BRIGHTNESS", 2, 2, GL_SHORT);
        attributes.addAttribute("ENTITY_DATA_1", 1, 4, GL_UNSIGNED_INT);
        attributes.addAttribute("ENTITY_DATA_2", 1, 4, GL_UNSIGNED_INT);
        attributes.addAttribute("NORMAL", 3, 4, GL_FLOAT);
        attributes.addAttribute("TANGENT", 4, 4, GL_FLOAT);
        attributes.addAttribute("MIDTEXTURE", 2, 4, GL_FLOAT);
    }
}
