package makamys.neodymium.renderer;

import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.config.Config;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.Util;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import java.util.Locale;

public class MeshQuad {
    public final static int DEFAULT_BRIGHTNESS = Util.createBrightness(15, 15);
    public final static int DEFAULT_COLOR = 0xFFFFFFFF;

    //region common

    public float[] xs = new float[4];
    public float[] ys = new float[4];
    public float[] zs = new float[4];
    public float[] us = new float[4];
    public float[] vs = new float[4];
    public int[] cs = new int[4];
    // TODO normals?
    public int[] bs = new int[4];

    //endregion common

    //region RPLE

    // bs used as RED
    public int[] bsG = new int[4];
    public int[] bsB = new int[4];

    //endregion RPLE

    //region Shaders

    public int[] e1 = new int[4];
    public int[] e2 = new int[4];

    public float[] xn = new float[4];
    public float[] yn = new float[4];
    public float[] zn = new float[4];

    public float[] xt = new float[4];
    public float[] yt = new float[4];
    public float[] zt = new float[4];
    public float[] wt = new float[4];

    public float[] um = new float[4];
    public float[] vm = new float[4];

    //endregion Shaders

    //region Shaders + RPLE

    public float[] ue = new float[4];
    public float[] ve = new float[4];

    //endregion Shaders + RPLE

    public boolean deleted;

    public QuadNormal normal;

    private static Vector3f vectorA = new Vector3f();
    private static Vector3f vectorB = new Vector3f();
    private static Vector3f vectorC = new Vector3f();

    public void setState(int[] rawBuffer, int tessellatorVertexSize, int offset, ChunkMesh.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        deleted = false;

        Neodymium.util.readMeshQuad(this, rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);

        if(xs[0] == xs[1] && xs[1] == xs[2] && xs[2] == xs[3] && ys[0] == ys[1] && ys[1] == ys[2] && ys[2] == ys[3]) {
            // ignore empty quads (e.g. alpha pass of EnderIO item conduits)
            deleted = true;
            return;
        }
        
        vectorA.set(xs[1] - xs[0], ys[1] - ys[0], zs[1] - zs[0]);
        vectorB.set(xs[2] - xs[1], ys[2] - ys[1], zs[2] - zs[1]);
        Vector3f.cross(vectorA, vectorB, vectorC);
        
        normal = QuadNormal.fromVector(vectorC);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }
    
    public static boolean isValid(MeshQuad q) {
        return q != null && !q.deleted;
    }
}
