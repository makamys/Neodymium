package makamys.neodymium.renderer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import org.lwjgl.util.vector.Vector3f;

import makamys.neodymium.config.Config;
import makamys.neodymium.util.BufferWriter;

/*
 * This is what a quad looks like.
 * 
 *  0--1
 *  |  |
 *  3--2
 * 
 * We can glue quads together, forming a megaquad.
 * In the fragment shader we need to know which quad of the megaquad we are operating on.
 * For this reason, we store the "megaquad X" and "megaquad Y" coordinates in the vertices.
 * Their values at vertex 0: (0, 0)
 * Their values at vertex 1: (megaquad width, 0)
 * Their values at vertex 2: (megaquad width, megaquad height)
 * Their values at vertex 3: (0, megaquad height)
 */

public class MeshQuad {
    public float[] xs = new float[4];
    public float[] ys = new float[4];
    public float[] zs = new float[4];
    public float minX = Float.POSITIVE_INFINITY;
    public float minY = Float.POSITIVE_INFINITY;
    public float minZ = Float.POSITIVE_INFINITY;
    public float maxX = Float.NEGATIVE_INFINITY;
    public float maxY = Float.NEGATIVE_INFINITY;
    public float maxZ = Float.NEGATIVE_INFINITY;
    public float[] us = new float[4];
    public float[] vs = new float[4];
    public int[] bs = new int[4];
    public int[] cs = new int[4];
    // TODO normals?
    public boolean deleted;
    public boolean noMerge;
    
    public QuadNormal normal;
    public int offset;
    public ChunkMesh.Flags flags;
    
    // Is positive U direction parallel to edge 0-1?
    public boolean uDirectionIs01;
    
    public boolean isRectangle;
    
    // 0: quads glued together on edge 1-2 or 3-0 ("megaquad row length")
    // 1: quads glued together on edge 0-1 or 2-3 ("megaquad column length")
    private int[] quadCountByDirection = {1, 1}; 
    public static int[] totalMergeCountByPlane = new int[3];
    
    // When we merge with another quad, we forget what we used to be like.
    // Keep a reference to the quad we first merged with, and use it as a reminder.
    public MeshQuad mergeReference;
    
    private static Vector3f vectorA = new Vector3f();
    private static Vector3f vectorB = new Vector3f();
    private static Vector3f vectorC = new Vector3f();
    
    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ) {
        for(int vi = 0; vi < 4; vi++) {
            int i = offset + vi * 8;
            
            xs[vi] = Float.intBitsToFloat(rawBuffer[i + 0]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;
            
            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);
            
            bs[vi] = rawBuffer[i + 7];
            cs[vi] = rawBuffer[i + 5];
            
            i += 8;
        }
    }
    
    public void setState(int[] rawBuffer, int offset, ChunkMesh.Flags flags, float offsetX, float offsetY, float offsetZ) {
        resetState();
        
        read(rawBuffer, offset, offsetX, offsetY, offsetZ);
        
        uDirectionIs01 = us[0] != us[1];
        
        updateMinMaxXYZ();
        updateIsRectangle();
        if(!isRectangle) {
            // merging non-rectangles (e.g. Carpenter's Blocks wedge) is buggy, don't do it
            noMerge = true;
        }
        
        vectorA.set(xs[1] - xs[0], ys[1] - ys[0], zs[1] - zs[0]);
        vectorB.set(xs[2] - xs[1], ys[2] - ys[1], zs[2] - zs[1]);
        Vector3f.cross(vectorA, vectorB, vectorC);
        
        normal = QuadNormal.fromVector(vectorC);
    }
    
    private void resetState() {
        Arrays.fill(xs, 0);
        Arrays.fill(ys, 0);
        Arrays.fill(zs, 0);
        Arrays.fill(us, 0);
        Arrays.fill(vs, 0);
        Arrays.fill(bs, 0);
        Arrays.fill(cs, 0);
        
        minX = Float.POSITIVE_INFINITY;
        minY = Float.POSITIVE_INFINITY;
        minZ = Float.POSITIVE_INFINITY;
        maxX = Float.NEGATIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        maxZ = Float.NEGATIVE_INFINITY;
        
        deleted = noMerge = false;
        normal = null;
        offset = 0;
        flags = null;
        uDirectionIs01 = false;
        Arrays.fill(quadCountByDirection, 1);
        Arrays.fill(totalMergeCountByPlane, 0);
        mergeReference = null;
    }
    
    public void writeToBuffer(BufferWriter out) throws IOException {
        for(int vertexI = 0; vertexI < 4; vertexI++) {
            int vi = vertexI;
            int provokingI = 3;
            
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
            
            if(Config.simplifyChunkMeshes) {
                if((quadCountByUVDirection(false) == 1 && quadCountByUVDirection(true) == 1)) {
                    // let the fragment shader know this is not a megaquad
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                } else {
                    out.writeByte(us[vi] == us[provokingI] ? 0 : (byte)quadCountByUVDirection(false));
                    out.writeByte(vs[vi] == vs[provokingI] ? 0 : (byte)quadCountByUVDirection(true));
                    out.writeByte(us[vi] == us[provokingI] ? (byte)0 : 1);
                    out.writeByte(vs[vi] == vs[provokingI] ? (byte)0 : 1);
                }
            }
            
            assert out.position() % getStride() == 0;
            
            //System.out.println("[" + vertexI + "] x: " + x + ", y: " + y + " z: " + z + ", u: " + u + ", v: " + v + ", b: " + b + ", c: " + c);
        }
    }
    
    public int quadCountByUVDirection(boolean v) {
        if(v) {
            return quadCountByDirection[uDirectionIs01 ? 0 : 1];
        } else {
            return quadCountByDirection[uDirectionIs01 ? 1 : 0];
        }
    }
    
    public static int getStride() {
        return
                3 * 4                                       // XYZ          (float)
                + 2 * (Config.shortUV ? 2 : 4)              // UV           (float)
                + 4                                         // B            (int)
                + 4                                         // C            (int)
                + (Config.simplifyChunkMeshes ? 4 : 0)      // megaquad XY  (byte)
                ;
    }
    
    private boolean isTranslatedCopyOf(MeshQuad o, boolean checkValid) {
        if((!isValid(this) && checkValid) || !isValid(o) || normal != o.normal) return false;
        
        if(mergeReference != null) {
            return mergeReference.isTranslatedCopyOf(o, false);
        }
        
        for(int i = 1; i < 4; i++) {
            double relX = xs[i] - xs[0];
            double relY = ys[i] - ys[0];
            double relZ = zs[i] - zs[0];
            
            if(o.xs[i] != o.xs[0] + relX || o.ys[i] != o.ys[0] + relY || o.zs[i] != o.zs[0] + relZ) {
                return false;
            }
        }
        
        for(int i = 0; i < 4; i++) {
            if(us[i] != o.us[i] || vs[i] != o.vs[i] || bs[i] != o.bs[i] || cs[i] != o.cs[i]) {
                return false;
            }
        }
        
        return true;
    }
    
    public void tryToMerge(MeshQuad o) {
        if(noMerge || o.noMerge) return;
        
        if(isTranslatedCopyOf(o, true)) {
            int numVerticesTouching = 0;
            boolean[] verticesTouching = new boolean[4];
            for(int i = 0; i < 4; i++) {
                for(int j = 0; j < 4; j++) {
                    if(xs[i] == o.xs[j] && ys[i] == o.ys[j] && zs[i] == o.zs[j]) {
                        verticesTouching[i] = true;
                        numVerticesTouching++;
                    }
                }
            }
            if(numVerticesTouching == 2) {
                for(int i = 0; i < 4; i++) {
                    if(verticesTouching[i]) {
                        copyVertexFrom(o, i, i);
                    }
                }
                
                if((verticesTouching[0] && verticesTouching[1]) || (verticesTouching[2] && verticesTouching[3])) {
                    quadCountByDirection[0] += o.quadCountByDirection[0];
                }
                if((verticesTouching[1] && verticesTouching[2]) || (verticesTouching[3] && verticesTouching[0])) {
                    quadCountByDirection[1] += o.quadCountByDirection[1];
                }
                
                totalMergeCountByPlane[getPlane().ordinal() - 1]++;
                
                mergeReference = o;
                
                o.deleted = true;
            }
        }
    }
    
    private void copyVertexFrom(MeshQuad o, int src, int dest) {
        xs[dest] = o.xs[src];
        ys[dest] = o.ys[src];
        zs[dest] = o.zs[src];
        us[dest] = o.us[src];
        vs[dest] = o.vs[src];
        bs[dest] = o.bs[src];
        cs[dest] = o.cs[src];
        
        updateMinMaxXYZ(); // TODO isn't doing this a waste? I should get rid of the min/maxXYZ variables entirely.
    }
    
    private void updateMinMaxXYZ() {
        for(int i = 0; i < 4; i++) {
            minX = Math.min(minX, xs[i]);
            minY = Math.min(minY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxX = Math.max(maxX, xs[i]);
            maxY = Math.max(maxY, ys[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
    }
    
    private void updateIsRectangle() {
        isRectangle =
                vertexExists(minX, minY, minZ) &&
                vertexExists(minX, minY, maxZ) &&
                vertexExists(minX, maxY, minZ) &&
                vertexExists(minX, maxY, maxZ) &&
                vertexExists(maxX, minY, minZ) &&
                vertexExists(maxX, minY, maxZ) &&
                vertexExists(maxX, maxY, minZ) &&
                vertexExists(maxX, maxY, maxZ);
    }
    
    private boolean vertexExists(float x, float y, float z) {
        for(int i = 0; i < 4; i++) {
            if(xs[i] == x && ys[i] == y && zs[i] == z) {
                return true;
            }
        }
        return false;
    }
    
    // maybe minXYZ and maxXYZ should be arrays instead
    public double getMin(int coord) {
        return coord == 0 ? minX : coord == 1 ? minY : coord == 2 ? minZ : -1;
    }
    
    public double getMax(int coord) {
        return coord == 0 ? maxX : coord == 1 ? maxY : coord == 2 ? maxZ : -1;
    }
    
    public boolean onSamePlaneAs(MeshQuad o) {
        return isValid(this) && isValid(o) && getPlane() == o.getPlane() &&
            ((getPlane() == Plane.XY && minZ == o.minZ) ||
                    (getPlane() == Plane.XZ && minY == o.minY) ||
                    (getPlane() == Plane.YZ && minX == o.minX));
    }
    
    public Plane getPlane() {
        return Plane.fromNormal(normal);
    }
    
    public static boolean isValid(MeshQuad q) {
        return q != null && !q.deleted;
    }
    
    public boolean isClockwiseXZ() {
        return (xs[1] - xs[0]) * (zs[2] - zs[0]) - (xs[2] - xs[0]) * (zs[1] - zs[0]) < 0;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s(%.1f, %.1f, %.1f -- %.1f, %.1f, %.1f)", deleted ? "XXX " : "", minX, minY, minZ, maxX, maxY, maxZ);
        //return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }
    
    public static class QuadPlaneComparator implements Comparator<MeshQuad> {
        
        public static final QuadPlaneComparator[] quadPlaneComparators = new QuadPlaneComparator[]{
                new QuadPlaneComparator(2, 1, 0), // PLANE_XY -> ZYX
                new QuadPlaneComparator(1, 2, 0), // PLANE_XZ -> YZX
                new QuadPlaneComparator(0, 2, 1)  // PLANE_YZ -> XZY
        };

        private int c0, c1, c2;
        
        public QuadPlaneComparator(int firstCoordToCompare, int secondCoordToCompare, int thirdCoordToCompare) {
            this.c0 = firstCoordToCompare;
            this.c1 = secondCoordToCompare;
            this.c2 = thirdCoordToCompare;
        }
        
        @Override
        public int compare(MeshQuad a, MeshQuad b) {
            if(a.getMin(c0) < b.getMin(c0)) {
                return -1;
            } else if(a.getMin(c0) > b.getMin(c0)) {
                return 1;
            } else {
                if(a.getMin(c1) < b.getMin(c1)) {
                    return -1;
                } else if(a.getMin(c1) > b.getMin(c1)) {
                    return 1;
                } else {
                    if(a.getMin(c2) < b.getMin(c2)) {
                        return -1;
                    } else if(a.getMin(c2) > b.getMin(c2)) {
                        return 1;
                    } else {
                        return (int)Math.signum(a.offset - b.offset); 
                    }
                }
            }
        }
    }
    
    public static enum Plane {
        NONE,
        XY,
        XZ,
        YZ;
        
        public static Plane fromNormal(QuadNormal normal) {
            switch(normal) {
            case POSITIVE_X:
            case NEGATIVE_X:
                return YZ;
            case POSITIVE_Y:
            case NEGATIVE_Y:
                return XZ;
            case POSITIVE_Z:
            case NEGATIVE_Z:
                return XY;
            default:
                return NONE;
            }
        }
    }

    public boolean isPosEqual(MeshQuad b) {
        return Arrays.equals(xs, b.xs) && Arrays.equals(ys, b.ys) && Arrays.equals(zs, b.zs);
    }
}
