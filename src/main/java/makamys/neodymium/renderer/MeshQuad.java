package makamys.neodymium.renderer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

import makamys.neodymium.util.SpriteUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;

public class MeshQuad {
    public int spriteIndex;
    public String spriteName;
    public int[] xs = new int[4];
    public int[] ys = new int[4];
    public int[] zs = new int[4];
    public int baseX = -1, baseY, baseZ;
    public int minX = Integer.MAX_VALUE;
    public int minY = Integer.MAX_VALUE;
    public int minZ = Integer.MAX_VALUE;
    public int maxX = Integer.MIN_VALUE;
    public int maxY = Integer.MIN_VALUE;
    public int maxZ = Integer.MIN_VALUE;
    public int[] relUs = new int[4];
    public int[] relVs = new int[4];
    public int[] bUs = new int[4];
    public int[] bVs = new int[4];
    public int[] cs = new int[4];
    public int[] normals = new int[4];
    public boolean deleted;
    public boolean isFullQuad;
    
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
    
    public MeshQuad(int[] rawBuffer, int offset, ChunkMesh.Flags flags, int offsetX, int offsetY, int offsetZ) {
        this.offset = offset;
        this.flags = flags;
        int i = offset;
        float[] us = new float[4];
        float uSum = 0;
        float[] vs = new float[4];
        float vSum = 0;
        for(int vertexI = 0; vertexI < 4; vertexI++) {
            float u = Float.intBitsToFloat(rawBuffer[vertexI * 8 + i + 3]);
            float v = Float.intBitsToFloat(rawBuffer[vertexI * 8 + i + 4]);
            
            us[vertexI] = u;
            vs[vertexI] = v;
            
            uSum += u;
            vSum += v;
        }
        
        float avgU = uSum / 4f;
        float avgV = vSum / 4f;
        
        TextureAtlasSprite sprite = null;
        Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
        
        spriteIndex = SpriteUtil.getSpriteIndexForUV(avgU, avgV);
        sprite = SpriteUtil.getSprite(spriteIndex);
        
        if(sprite == null) {
            System.out.println("Error: couldn't find sprite");
        } else {
            spriteName = sprite.getIconName();
            for(int vertexI = 0; vertexI < 4; vertexI++) {
                float x = Float.intBitsToFloat(rawBuffer[i + 0]) - offsetX;
                float y = Float.intBitsToFloat(rawBuffer[i + 1]) - offsetY;
                float z = Float.intBitsToFloat(rawBuffer[i + 2]) - offsetZ;
                
                int simpleX = (int)(x * 16);
                //if(simpleX == 256) simpleX = 255;
                int simpleY = (int)(y * 16);
                //if(simpleY == 256) simpleY = 255;
                int simpleZ = (int)(z * 16);
                //if(simpleZ == 256) simpleZ = 255;
                
                xs[vertexI] = simpleX;
                ys[vertexI] = simpleY;
                zs[vertexI] = simpleZ;
                
                // hasTexture
                float u = us[vertexI];
                float v = vs[vertexI];
                
                int simpleRelU = (int)((u - sprite.getMinU()) / (sprite.getMaxU() - sprite.getMinU()) * 16);
                int simpleRelV = (int)((v - sprite.getMinV()) / (sprite.getMaxV() - sprite.getMinV()) * 16);
                if(flags.hasTexture) {
                    relUs[vertexI] = simpleRelU;
                    relVs[vertexI] = simpleRelV;
                }
                
                // hasBrightness
                int brightness = rawBuffer[i + 7];
                int brightnessU = brightness & 0xFFFF;
                int brightnessV = (brightness >> 16) & 0xFFFF;
                if(flags.hasBrightness) {
                    bUs[vertexI] = (int)brightnessU;
                    bVs[vertexI] = (int)brightnessV;
                }

                // hasColor
                int color = rawBuffer[i + 5];
                if(flags.hasColor) {
                    cs[vertexI] = color;
                }

                // hasNormals
                int normal = rawBuffer[i + 6];
                if(flags.hasNormals) {
                    normals[vertexI] = normal;
                }
                
                i += 8;
            }
        }
        
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
        
        boolean equalToAABB = true;
        for(int minOrMaxX = 0; minOrMaxX < 2; minOrMaxX++) {
            for(int minOrMaxY = 0; minOrMaxY < 2; minOrMaxY++) {
                for(int minOrMaxZ = 0; minOrMaxZ < 2; minOrMaxZ++) {
                    if(getCornerVertex(minOrMaxX == 1, minOrMaxY == 1, minOrMaxZ == 1) == -1) {
                        equalToAABB = false;
                        break;
                    }
                }
            }
        }
        
        switch(plane) {
        case PLANE_XY:
            isFullQuad = equalToAABB && (maxX - minX) == 16 && (maxY - minY) == 16;
            break;
        case PLANE_XZ:
            isFullQuad = equalToAABB && (maxX - minX) == 16 && (maxZ - minZ) == 16;
            break;
        case PLANE_YZ:
            isFullQuad = equalToAABB && (maxY - minY) == 16 && (maxZ - minZ) == 16;
            break;
        default:
            isFullQuad = false;
        }
        
        for(int c = 0; c < 3; c++) {
            if(getMin(c) < 0 || getMax(c) < 0 || getMax(c) - getMin(c) > 16 || getMin(c) > 256 || getMax(c) > 256) {
                this.deleted = true;
                // TODO handle weirdness more gracefully
            }
        }
    }
    
    // yeah this is kinda unoptimal
    private int getCornerVertex(boolean minOrMaxX, boolean minOrMaxY, boolean minOrMaxZ) {
        int aabbCornerX = !minOrMaxX ? minX : maxX;
        int aabbCornerY = !minOrMaxY ? minY : maxY;
        int aabbCornerZ = !minOrMaxZ ? minZ : maxZ;
        
        for(int vi = 0; vi < 4; vi++) {
            if(xs[vi] == aabbCornerX && ys[vi] == aabbCornerY && zs[vi] == aabbCornerZ) {
                return vi;
            }
        }
        return -1;
    }
    
    public void tryToMerge(MeshQuad o) {
        if(isValid(this) && isValid(o) && plane == o.plane 
                && spriteIndex == o.spriteIndex && isFullQuad && o.isFullQuad) {
            int numVerticesTouching = 0;
            for(int i = 0; i < 4; i++) {
                for(int j = 0; j < 4; j++) {
                    if(xs[i] == o.xs[j] && ys[i] == o.ys[j] && zs[i] == o.zs[j]) {
                        numVerticesTouching++;
                    }
                }
            }
            if(numVerticesTouching == 2) {
                mergeWithQuad(o);
                
                totalMergeCountByPlane[plane]++;
                
                o.deleted = true;
            }
        }
    }
    
    private void mergeWithQuad(MeshQuad o) {
        if(minX < o.minX) {
            copyEdgeFrom(o, EnumFacing.EAST);
        } else if(minX > o.minX) {
            copyEdgeFrom(o, EnumFacing.WEST);
        } else if(minY < o.minY) {
            copyEdgeFrom(o, EnumFacing.UP);
        } else if(minY > o.minY) {
            copyEdgeFrom(o, EnumFacing.DOWN);
        } else if(minZ < o.minZ) {
            copyEdgeFrom(o, EnumFacing.NORTH);
        } else if(minX > o.minX) {
            copyEdgeFrom(o, EnumFacing.SOUTH);
        }
    }
    
    private void copyEdgeFrom(MeshQuad o, EnumFacing side) {
        int whichX, whichY, whichZ;
        whichX = whichY = whichZ = -1;
        
        switch(plane) {
        case PLANE_XY:
            whichZ = 0;
            break;
        case PLANE_XZ:
            whichY = 0;
            break;
        case PLANE_YZ:
            whichX = 0;
            break;
        }
        
        switch(side) {
        case EAST:
            copyCornerVertexFrom(o, 1, whichY, whichZ);
            break;
        case WEST:
            copyCornerVertexFrom(o, 0, whichY, whichZ);
            break;
        case UP:
            copyCornerVertexFrom(o, whichX, 1, whichZ);
            break;
        case DOWN:
            copyCornerVertexFrom(o, whichX, 0, whichZ);
            break;
        case NORTH:
            copyCornerVertexFrom(o, whichX, whichY, 1);
            break;
        case SOUTH:
            copyCornerVertexFrom(o, whichX, whichY, 0);
            break;
        }
        
        updateMinMaxXYZ();
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
    
    private void copyCornerVertexFrom(MeshQuad o, int whichX, int whichY, int whichZ) {
        int whichXMin, whichXMax, whichYMin, whichYMax, whichZMin, whichZMax;
        whichXMin = whichYMin = whichZMin = 0;
        whichXMax = whichYMax = whichZMax = 1;
        
        if(whichX != -1) whichXMin = whichXMax = whichX;
        if(whichY != -1) whichYMin = whichYMax = whichY;
        if(whichZ != -1) whichZMin = whichZMax = whichZ;
        
        for(int minOrMaxX = whichXMin; minOrMaxX <= whichXMax; minOrMaxX++) {
            for(int minOrMaxY = whichYMin; minOrMaxY <= whichYMax; minOrMaxY++) {
                for(int minOrMaxZ = whichZMin; minOrMaxZ <= whichZMax; minOrMaxZ++) {
                    copyVertexFrom(o, 
                            o.getCornerVertex(minOrMaxX == 1, minOrMaxY == 1, minOrMaxZ == 1),
                            getCornerVertex(minOrMaxX == 1, minOrMaxY == 1, minOrMaxZ == 1));
                }
            }
        }
    }
    
    private void copyVertexFrom(MeshQuad o, int src, int dest) {
        xs[dest] = o.xs[src];
        ys[dest] = o.ys[src];
        zs[dest] = o.zs[src];
        relUs[dest] = o.relUs[src];
        relVs[dest] = o.relVs[src];
        bUs[dest] = o.bUs[src];
        bVs[dest] = o.bVs[src];
        cs[dest] = o.cs[src];
        normals[dest] = o.normals[src];
    }
    
    public void writeToDisk(DataOutputStream out, int pass) throws IOException {
        if(deleted) {
            return;
        }
        
        if(flags.hasTexture) {
            if(pass == 0) out.writeShort(spriteIndex);
        }
        for (int vertexI = 0; vertexI < 4; vertexI++) {
            if(pass == 1) out.writeByte(xs[vertexI] == 256 ? 255 : xs[vertexI]);
            if(pass == 2) out.writeByte(ys[vertexI] == 256 ? 255 : ys[vertexI]);
            if(pass == 3) out.writeByte(zs[vertexI] == 256 ? 255 : zs[vertexI]);

            if (flags.hasTexture) {
                if(pass == 4) out.writeByte(relUs[vertexI]);
                if(pass == 5) out.writeByte(relVs[vertexI]);
            }

            if (flags.hasBrightness) {
                if(pass == 6) out.writeByte(bUs[vertexI]);
                if(pass == 7) out.writeByte(bVs[vertexI]);
            }

            if (flags.hasColor) {
                if(pass == 8) out.writeInt(cs[vertexI]);
            }
            
            if (flags.hasNormals) {
                if(pass == 9) out.writeInt(normals[vertexI]);
            }
        }
    }
    
    // maybe minXYZ and maxXYZ should be arrays instead
    public int getMin(int coord) {
        return coord == 0 ? minX : coord == 1 ? minY : coord == 2 ? minZ : -1;
    }
    
    public int getMax(int coord) {
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
        return String.format(Locale.ENGLISH, "%s(%.1f, %.1f, %.1f -- %.1f, %.1f, %.1f) %s", deleted ? "XXX " : "", minX/16f, minY/16f, minZ/16f, maxX/16f, maxY/16f, maxZ/16f, spriteName);
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
