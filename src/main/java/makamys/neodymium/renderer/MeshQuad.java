package makamys.neodymium.renderer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import makamys.neodymium.config.Config;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.Util;

public class MeshQuad {
    private final static int DEFAULT_BRIGHTNESS = Util.createBrightness(15, 15);
    private final static int DEFAULT_COLOR = 0xFFFFFFFF;
    
    public float[] xs = new float[4];
    public float[] ys = new float[4];
    public float[] zs = new float[4];
    public float[] us = new float[4];
    public float[] vs = new float[4];
    public int[] cs = new int[4];
    // TODO normals?
    public int[] bs = new int[4];

    public boolean deleted;

    public QuadNormal normal;

    private static Vector3f vectorA = new Vector3f();
    private static Vector3f vectorB = new Vector3f();
    private static Vector3f vectorC = new Vector3f();
    
    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, ChunkMesh.Flags flags) {
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * 8;
            
            xs[vi] = Float.intBitsToFloat(rawBuffer[i + 0]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;
            
            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;

            // TODO normals?

            bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;

            i += 8;
        }
        
        if(vertices == 3) {
            // Quadrangulate! 
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];
            
            us[3] = us[2];
            vs[3] = vs[2];
            
            bs[3] = bs[2];
            cs[3] = cs[2];
        }
    }
    
    public void setState(int[] rawBuffer, int offset, ChunkMesh.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        deleted = false;

        read(rawBuffer, offset, offsetX, offsetY, offsetZ, drawMode, flags);
        
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
    
    public void writeToBuffer(BufferWriter out) throws IOException {
        for(int vertexI = 0; vertexI < 4; vertexI++) {
            int vi = vertexI;
            
            float x = xs[vi];
            float y = ys[vi];
            float z = zs[vi];
            
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            
            float u = us[vi];
            float v = vs[vi];
            
            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }
            
            int b = bs[vi];
            
            out.writeInt(b);

            int c = cs[vi];
            
            out.writeInt(c);
            
            assert out.position() % getStride() == 0;
            
            //System.out.println("[" + vertexI + "] x: " + x + ", y: " + y + " z: " + z + ", u: " + u + ", v: " + v + ", b: " + b + ", c: " + c);
        }
    }
    
    public static int getStride() {
        return
                3 * 4                                       // XYZ          (float)
                + 2 * (Config.shortUV ? 2 : 4)              // UV           (float)
                + 4                                         // B            (int)
                + 4                                         // C            (int)
                ;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }
    
    public static boolean isValid(MeshQuad q) {
        return q != null && !q.deleted;
    }
}
