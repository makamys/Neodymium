package makamys.neodymium.renderer;

import lombok.experimental.UtilityClass;
import lombok.val;
import makamys.neodymium.Neodymium;
import makamys.neodymium.util.Util;
import org.lwjgl.util.vector.Vector3f;

import static makamys.neodymium.renderer.compat.RenderUtil.QUAD_OFFSET_XPOS;
import static makamys.neodymium.renderer.compat.RenderUtil.QUAD_OFFSET_YPOS;
import static makamys.neodymium.renderer.compat.RenderUtil.QUAD_OFFSET_ZPOS;

@UtilityClass
public final class MeshQuad {
    public final static int DEFAULT_BRIGHTNESS = Util.createBrightness(15, 15);
    public final static int DEFAULT_COLOR = 0xFFFFFFFF;


    private static class Vectors {
        public final Vector3f A = new Vector3f();
        public final Vector3f B = new Vector3f();
        public final Vector3f C = new Vector3f();
    }

    private static final ThreadLocal<Vectors> VECTORS = ThreadLocal.withInitial(Vectors::new);

    public static boolean processQuad(int[] tessBuffer, int tessOffset, int[] quadBuffer, int quadOffset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        val util = Neodymium.util;
        util.readMeshQuad(tessBuffer, tessOffset, quadBuffer, quadOffset, offsetX, offsetY, offsetZ, drawMode, flags);
        int stride = util.vertexSizeInQuadBuffer();
        boolean deleted = true;
        for (int i = 1; i < 4; i++) {
            int offset = quadOffset + stride * i;
            if (quadBuffer[quadOffset + QUAD_OFFSET_XPOS] != quadBuffer[offset + QUAD_OFFSET_XPOS] ||
                quadBuffer[quadOffset + QUAD_OFFSET_YPOS] != quadBuffer[offset + QUAD_OFFSET_YPOS] ||
                quadBuffer[quadOffset + QUAD_OFFSET_ZPOS] != quadBuffer[offset + QUAD_OFFSET_ZPOS]) {
                deleted = false;
                break;
            }
        }

        if (deleted)
            return true;

        float X0 = Float.intBitsToFloat(quadBuffer[quadOffset + QUAD_OFFSET_XPOS]);
        float Y0 = Float.intBitsToFloat(quadBuffer[quadOffset + QUAD_OFFSET_YPOS]);
        float Z0 = Float.intBitsToFloat(quadBuffer[quadOffset + QUAD_OFFSET_ZPOS]);
        float X1 = Float.intBitsToFloat(quadBuffer[quadOffset + stride + QUAD_OFFSET_XPOS]);
        float Y1 = Float.intBitsToFloat(quadBuffer[quadOffset + stride + QUAD_OFFSET_YPOS]);
        float Z1 = Float.intBitsToFloat(quadBuffer[quadOffset + stride + QUAD_OFFSET_ZPOS]);
        float X2 = Float.intBitsToFloat(quadBuffer[quadOffset + stride * 2 + QUAD_OFFSET_XPOS]);
        float Y2 = Float.intBitsToFloat(quadBuffer[quadOffset + stride * 2 + QUAD_OFFSET_YPOS]);
        float Z2 = Float.intBitsToFloat(quadBuffer[quadOffset + stride * 2 + QUAD_OFFSET_ZPOS]);

        val vectors = VECTORS.get();
        vectors.A.set(X1 - X0, Y1 - Y0, Z1 - Z0);
        vectors.B.set(X2 - X1, Y2 - Y1, Z2 - Z1);
        Vector3f.cross(vectors.A, vectors.B, vectors.C);

        quadBuffer[quadOffset + stride * 4] = QuadNormal.fromVector(vectors.C).ordinal();

        return false;
    }
}
