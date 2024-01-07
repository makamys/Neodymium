package makamys.neodymium.renderer;

import makamys.neodymium.Compat;
import makamys.neodymium.config.Config;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.Util;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import java.util.Locale;

public class MeshQuad {
    private final static int DEFAULT_BRIGHTNESS = Util.createBrightness(15, 15);
    private final static int DEFAULT_COLOR = 0xFFFFFFFF;

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


    private void read(int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        boolean rple = Compat.isRPLEModPresent();
        boolean optiFineShaders = Compat.isOptiFineShadersEnabled();

        if (rple && optiFineShaders) {
            readRPLEAndShaders(rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        } else if (optiFineShaders) {
            readShaders(rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        } else if (rple) {
            readRPLE(rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        } else {
            readVanilla(rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        }
    }

    //region read implementations

    private void readRPLEAndShaders(int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        //RPLE and Shaders
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            bs[vi] = flags.hasBrightness ? rawBuffer[i + 6] : DEFAULT_BRIGHTNESS;

            e1[vi] = rawBuffer[i + 7];
            e2[vi] = rawBuffer[i + 8];

            xn[vi] = Float.intBitsToFloat(rawBuffer[i + 9]);
            yn[vi] = Float.intBitsToFloat(rawBuffer[i + 10]);
            zn[vi] = Float.intBitsToFloat(rawBuffer[i + 11]);

            xt[vi] = Float.intBitsToFloat(rawBuffer[i + 12]);
            yt[vi] = Float.intBitsToFloat(rawBuffer[i + 13]);
            zt[vi] = Float.intBitsToFloat(rawBuffer[i + 14]);
            wt[vi] = Float.intBitsToFloat(rawBuffer[i + 15]);

            um[vi] = Float.intBitsToFloat(rawBuffer[i + 16]);
            vm[vi] = Float.intBitsToFloat(rawBuffer[i + 17]);

            if (flags.hasBrightness) {
                bsG[vi] = rawBuffer[i + 18];
                bsB[vi] = rawBuffer[i + 19];
            } else {
                bsG[vi] = DEFAULT_BRIGHTNESS;
                bsB[vi] = DEFAULT_BRIGHTNESS;
            }

            ue[vi] = Float.intBitsToFloat(rawBuffer[i + 20]);
            ve[vi] = Float.intBitsToFloat(rawBuffer[i + 21]);
        }

        if(vertices == 3) {
            // Quadrangulate!
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];

            us[3] = us[2];
            vs[3] = vs[2];

            cs[3] = cs[2];

            bs[3] = bs[2];

            e1[3] = e1[2];
            e2[3] = e2[2];

            xn[3] = xn[2];
            yn[3] = yn[2];
            zn[3] = zn[2];

            xt[3] = xt[2];
            yt[3] = yt[2];
            zt[3] = zt[2];
            wt[3] = wt[2];

            um[3] = um[2];
            vm[3] = vm[2];

            bsG[3] = bsG[2];
            bsB[3] = bsB[2];

            ue[3] = ue[2];
            ve[3] = ve[2];
        }
    }

    private void readShaders(int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        //Only shaders
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for (int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            bs[vi] = flags.hasBrightness ? rawBuffer[i + 6] : DEFAULT_BRIGHTNESS;
            e1[vi] = rawBuffer[i + 7];
            e2[vi] = rawBuffer[i + 8];
            xn[vi] = Float.intBitsToFloat(rawBuffer[i + 9]);
            yn[vi] = Float.intBitsToFloat(rawBuffer[i + 10]);
            zn[vi] = Float.intBitsToFloat(rawBuffer[i + 11]);
            xt[vi] = Float.intBitsToFloat(rawBuffer[i + 12]);
            yt[vi] = Float.intBitsToFloat(rawBuffer[i + 13]);
            zt[vi] = Float.intBitsToFloat(rawBuffer[i + 14]);
            wt[vi] = Float.intBitsToFloat(rawBuffer[i + 15]);
            um[vi] = Float.intBitsToFloat(rawBuffer[i + 16]);
            vm[vi] = Float.intBitsToFloat(rawBuffer[i + 17]);
        }

        if (vertices == 3) {
            // Quadrangulate!
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];

            us[3] = us[2];
            vs[3] = vs[2];

            cs[3] = cs[2];

            bs[3] = bs[2];

            e1[3] = e1[2];
            e2[3] = e2[2];

            xn[3] = xn[2];
            yn[3] = yn[2];
            zn[3] = zn[2];

            xt[3] = xt[2];
            yt[3] = yt[2];
            zt[3] = zt[2];
            wt[3] = wt[2];

            um[3] = um[2];
            vm[3] = vm[2];
        }
    }

    private void readRPLE(int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        //Only RPLE
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            // TODO normals?

            if (flags.hasBrightness) {
                bs[vi] = rawBuffer[i + 7];
                bsG[vi] = rawBuffer[i + 8];
                bsB[vi] = rawBuffer[i + 9];
            } else {
                bs[vi] = DEFAULT_BRIGHTNESS;
                bsG[vi] = DEFAULT_BRIGHTNESS;
                bsB[vi] = DEFAULT_BRIGHTNESS;
            }
        }

        if(vertices == 3) {
            // Quadrangulate!
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];

            us[3] = us[2];
            vs[3] = vs[2];

            cs[3] = cs[2];

            bs[3] = bs[2];
            bsG[3] = bsG[2];
            bsB[3] = bsB[2];
        }
    }

    private void readVanilla(int[] rawBuffer, int tessellatorVertexSize, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        //No RPLE or Shaders
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * tessellatorVertexSize;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            // TODO normals?

            bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;
        }

        if(vertices == 3) {
            // Quadrangulate!
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];

            us[3] = us[2];
            vs[3] = vs[2];

            cs[3] = cs[2];

            bs[3] = bs[2];
        }
    }

    //endregion read implementations
    
    public void setState(int[] rawBuffer, int tessellatorVertexSize, int offset, ChunkMesh.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        deleted = false;

        read(rawBuffer, tessellatorVertexSize, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        
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

    /**
     * @implSpec These needs to be kept in sync with the attributes in {@link NeoRenderer#init()}
     */
    public void writeToBuffer(BufferWriter out, int expectedStride) {
        boolean rple = Compat.isRPLEModPresent();
        boolean shaders = Compat.isOptiFineShadersEnabled();

        if (rple && shaders) {
            writeToBufferRPLEAndShaders(out, expectedStride);
        } else if (shaders) {
            writeToBufferShaders(out, expectedStride);
        } else if (rple) {
            writeToBufferRPLE(out, expectedStride);
        } else {
            writeToBufferVanilla(out, expectedStride);
        }
    }

    //region writeToBuffer implementations

    public void writeToBufferRPLEAndShaders(BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(xs[vi]);
            out.writeFloat(ys[vi]);
            out.writeFloat(zs[vi]);

            out.writeFloat(us[vi]);
            out.writeFloat(vs[vi]);

            out.writeInt(cs[vi]);

            out.writeInt(bs[vi]);

            out.writeInt(e1[vi]);
            out.writeInt(e2[vi]);

            out.writeFloat(xn[vi]);
            out.writeFloat(yn[vi]);
            out.writeFloat(zn[vi]);

            out.writeFloat(xt[vi]);
            out.writeFloat(yt[vi]);
            out.writeFloat(zt[vi]);
            out.writeFloat(wt[vi]);

            out.writeFloat(um[vi]);
            out.writeFloat(vm[vi]);

            out.writeInt(bsG[vi]);
            out.writeInt(bsB[vi]);

            out.writeFloat(ue[vi]);
            out.writeFloat(ve[vi]);

            assert out.position() % expectedStride == 0;
        }
    }

    public void writeToBufferShaders(BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(xs[vi]);
            out.writeFloat(ys[vi]);
            out.writeFloat(zs[vi]);

            out.writeFloat(us[vi]);
            out.writeFloat(vs[vi]);

            out.writeInt( cs[vi]);

            out.writeInt(bs[vi]);

            out.writeInt(e1[vi]);
            out.writeInt(e2[vi]);

            out.writeFloat(xn[vi]);
            out.writeFloat(yn[vi]);
            out.writeFloat(zn[vi]);

            out.writeFloat(xt[vi]);
            out.writeFloat(yt[vi]);
            out.writeFloat(zt[vi]);
            out.writeFloat(wt[vi]);

            out.writeFloat(um[vi]);
            out.writeFloat(vm[vi]);

            assert out.position() % expectedStride == 0;
        }
    }

    public void writeToBufferRPLE(BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(xs[vi]);
            out.writeFloat(ys[vi]);
            out.writeFloat(zs[vi]);

            float u = us[vi];
            float v = vs[vi];

            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            out.writeInt(cs[vi]);

            out.writeInt(bs[vi]);
            out.writeInt(bsG[vi]);
            out.writeInt(bsB[vi]);

            assert out.position() % expectedStride == 0;
        }
    }

    public void writeToBufferVanilla(BufferWriter out, int expectedStride) {
        for(int vi = 0; vi < 4; vi++) {
            out.writeFloat(xs[vi]);
            out.writeFloat(ys[vi]);
            out.writeFloat(zs[vi]);

            float u = us[vi];
            float v = vs[vi];

            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            out.writeInt(cs[vi]);

            out.writeInt(bs[vi]);

            assert out.position() % expectedStride == 0;
        }
    }

    //endregion

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }
    
    public static boolean isValid(MeshQuad q) {
        return q != null && !q.deleted;
    }
}
