package makamys.neodymium.renderer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.SpriteUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;

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
    
    public static final int PLANE_NONE = -1, PLANE_XY = 0, PLANE_XZ = 1, PLANE_YZ = 2;
    public int plane = PLANE_NONE;
    public int offset;
    public ChunkMesh.Flags flags;
    
    public static int[] totalMergeCountByPlane = new int[3];
    
    private int minPositive(int a, int b) {
        if(a == -1) {
            return b;
        } else {
            return a < b ? a : b;
        }
    }
    private int maxPositive(int a, int b) {
        if(a == -1) {
            return b;
        } else {
            return a > b ? a : b;
        }
    }
    
    private void read(int[] rawBuffer, int offset, int offsetX, int offsetY, int offsetZ) {
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
    
    public MeshQuad(int[] rawBuffer, int offset, ChunkMesh.Flags flags, int offsetX, int offsetY, int offsetZ) {
        read(rawBuffer, offset, offsetX, offsetY, offsetZ);
        
        updateMinMaxXYZ();
        
        if(ys[0] == ys[1] && ys[1] == ys[2] && ys[2] == ys[3]) {
            plane = PLANE_XZ;
        } else if(xs[0] == xs[1] && xs[1] == xs[2] && xs[2] == xs[3]) {
            plane = PLANE_YZ;
        } else if(zs[0] == zs[1] && zs[1] == zs[2] && zs[2] == zs[3]) {
            plane = PLANE_XY;
        } else {
            plane = PLANE_NONE;
        }
    }
    
    public void writeToBuffer(BufferWriter out) throws IOException {
        for(int vertexI = 0; vertexI < 6; vertexI++) {
            int vi = new int[]{0, 1, 2, 0, 2, 3}[vertexI];
            
            float x = xs[vi];
            float y = ys[vi];
            float z = zs[vi];
            
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            
            float u = us[vi];
            float v = vs[vi];
            
            out.writeFloat(u);
            out.writeFloat(v);
            
            int b = bs[vi];
            
            out.writeInt(b);

            int c = cs[vi];
            
            out.writeInt(c);
            
            //System.out.println("[" + vertexI + "] x: " + x + ", y: " + y + " z: " + z + ", u: " + u + ", v: " + v + ", b: " + b + ", c: " + c);
        }
    }
    
    private boolean isTranslatedCopyOf(MeshQuad o) {
        if(!isValid(this) || !isValid(o) || plane != o.plane) return false;
        
        for(int i = 1; i < 4; i++) {
            double relX = xs[i] - xs[0];
            double relY = xs[i] - xs[0];
            double relZ = xs[i] - xs[0];
            
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
        if(isTranslatedCopyOf(o)) {
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
                
                totalMergeCountByPlane[plane]++;
                
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
    
    // maybe minXYZ and maxXYZ should be arrays instead
    public double getMin(int coord) {
        return coord == 0 ? minX : coord == 1 ? minY : coord == 2 ? minZ : -1;
    }
    
    public double getMax(int coord) {
        return coord == 0 ? maxX : coord == 1 ? maxY : coord == 2 ? maxZ : -1;
    }
    
    public boolean onSamePlaneAs(MeshQuad o) {
        return isValid(this) && isValid(o) && plane == o.plane &&
            ((plane == PLANE_XY && minZ == o.minZ) ||
                    (plane == PLANE_XZ && minY == o.minY) ||
                    (plane == PLANE_YZ && minX == o.minX));
    }
    
    // this should be static..
    public boolean isValid(MeshQuad q) {
        return q != null && !q.deleted;
    }
    
    public boolean isClockwiseXZ() {
        return (xs[1] - xs[0]) * (zs[2] - zs[0]) - (xs[2] - xs[0]) * (zs[1] - zs[0]) < 0;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s(%.1f, %.1f, %.1f -- %.1f, %.1f, %.1f)", deleted ? "XXX " : "", minX, minY, minZ, maxX, maxY, maxZ);
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
}
