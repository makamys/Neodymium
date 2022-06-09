package makamys.neodymium.renderer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.BufferUtils;

import makamys.neodymium.Config;
import makamys.neodymium.MixinConfigPlugin;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.util.BufferWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.tileentity.TileEntity;

/** A mesh for a 16x16x16 region of the world. */
public class ChunkMesh extends Mesh {
    
    Flags flags;
    
 // TODO move this somewhere else
    List<String> nameList = (List<String>) ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites.keySet().stream().collect(Collectors.toList());
    
    public static int usedRAM = 0;
    public static int instances = 0;
    
    public ChunkMesh(int x, int y, int z, Flags flags, int quadCount, ByteBuffer buffer, int pass) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.flags = flags;
        this.quadCount = quadCount;
        this.pass = pass;
        
        this.buffer = buffer;
        usedRAM += buffer.limit();
        instances++;
    }
    
    public ChunkMesh(int x, int y, int z, Flags flags, int quadCount, List<MeshQuad> quads, int pass) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.flags = flags;
        this.quadCount = quadCount;
        this.pass = pass;
        
        buffer = createBuffer(quads, quadCount);
        usedRAM += buffer.limit();
        instances++;
    }
    
    private static int totalOriginalQuadCount = 0;
    private static int totalSimplifiedQuadCount = 0;
    
    public static ChunkMesh fromTessellator(int pass, WorldRenderer wr, Tessellator t) {
        if(t.vertexCount % 4 != 0) {
            System.out.println("Error: Vertex count is not a multiple of 4");
            return null;
        }
        
        int xOffset = wr.posX;
        int yOffset = wr.posY;
        int zOffset = wr.posZ;
        
        boolean fr = MixinConfigPlugin.isOptiFinePresent() && Neodymium.ofFastRender;
        int tessellatorXOffset = fr ? xOffset : 0;
        int tessellatorYOffset = fr ? yOffset : 0;
        int tessellatorZOffset = fr ? zOffset : 0;
        
        boolean optimize = Config.simplifyChunkMeshes;
        
        ChunkMesh.Flags flags = new ChunkMesh.Flags(t.hasTexture, t.hasBrightness, t.hasColor, t.hasNormals);
        
        if(true) {
            List<MeshQuad> quads = new ArrayList<>();
            
            for(int quadI = 0; quadI < t.vertexCount / 4; quadI++) {
                MeshQuad quad = new MeshQuad(t.rawBuffer, quadI * 32, flags, wr.posX, wr.posY, wr.posZ);
                //if(quad.bUs[0] == quad.bUs[1] && quad.bUs[1] == quad.bUs[2] && quad.bUs[2] == quad.bUs[3] && quad.bUs[3] == quad.bVs[0] && quad.bVs[0] == quad.bVs[1] && quad.bVs[1] == quad.bVs[2] && quad.bVs[2] == quad.bVs[3] && quad.bVs[3] == 0) {
                //    quad.deleted = true;
                //}
                /*if(quad.plane == quad.PLANE_XZ && !quad.isClockwiseXZ()) {
                    // water hack
                    quad.deleted = true;
                }*/
                quads.add(quad);
            }
            
            ArrayList<ArrayList<MeshQuad>> quadsByPlaneDir = new ArrayList<>(); // XY, XZ, YZ
            for(int i = 0; i < 3; i++) {
                quadsByPlaneDir.add(new ArrayList<MeshQuad>());
            }
            for(MeshQuad quad : quads) {
                if(quad.plane != MeshQuad.Plane.NONE) {
                    quadsByPlaneDir.get(quad.plane.ordinal() - 1).add(quad);
                }
            }
            for(int plane = 0; plane < 3; plane++) {
                quadsByPlaneDir.get(plane).sort(MeshQuad.QuadPlaneComparator.quadPlaneComparators[plane]);
            }
            
            for(int plane = 0; plane < 3; plane++) {
                List<MeshQuad> planeDirQuads = quadsByPlaneDir.get(plane);
                int planeStart = 0;
                for(int quadI = 0; quadI < planeDirQuads.size(); quadI++) {
                    MeshQuad quad = planeDirQuads.get(quadI);
                    MeshQuad nextQuad = quadI == planeDirQuads.size() - 1 ? null : planeDirQuads.get(quadI + 1);
                    if(!quad.onSamePlaneAs(nextQuad)) {
                        simplifyPlane(planeDirQuads.subList(planeStart, quadI));
                        planeStart = quadI + 1;
                    }
                }
            }
            
            int quadCount = countValidQuads(quads);
            
            totalOriginalQuadCount += quads.size();
            totalSimplifiedQuadCount += quadCount;
            //System.out.println("simplified quads " + totalOriginalQuadCount + " -> " + totalSimplifiedQuadCount + " (ratio: " + ((float)totalSimplifiedQuadCount / (float)totalOriginalQuadCount) + ") totalMergeCountByPlane: " + Arrays.toString(totalMergeCountByPlane));
            
            if(quadCount > 0) {
                return new ChunkMesh(
                        (int)(xOffset / 16), (int)(yOffset / 16), (int)(zOffset / 16),
                        new ChunkMesh.Flags(t.hasTexture, t.hasBrightness, t.hasColor, t.hasNormals),
                        quadCount, quads, pass);
            } else {
                return null;
            }
        } else {
            int quadCount = t.vertexCount / 4;
            ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 6 * 7 * 4);
            BufferWriter out = new BufferWriter(buffer);
            
            try {
                for(int i = 0; i < quadCount; i++) {
                    writeBufferQuad(t, i * 32, out, -tessellatorXOffset + xOffset, -tessellatorYOffset + yOffset, -tessellatorZOffset + zOffset);
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
            buffer.flip();
            
            if(quadCount > 0) {
                return new ChunkMesh(
                        (int)(xOffset / 16), (int)(yOffset / 16), (int)(zOffset / 16),
                        flags,
                        quadCount, buffer, pass);
            } else {
                return null;
            }
        }
    }
    
    private static void writeBufferQuad(Tessellator t, int offset, BufferWriter out, float offsetX, float offsetY, float offsetZ) throws IOException {
        for(int vertexI = 0; vertexI < 6; vertexI++) {
            
            int vi = new int[]{3, 0, 1, 1, 2, 3}[vertexI];
            
            int i = offset + vi * 8;
            
            float x = Float.intBitsToFloat(t.rawBuffer[i + 0]) + offsetX;
            float y = Float.intBitsToFloat(t.rawBuffer[i + 1]) + offsetY;
            float z = Float.intBitsToFloat(t.rawBuffer[i + 2]) + offsetZ;
            
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            
            float u = Float.intBitsToFloat(t.rawBuffer[i + 3]);
            float v = Float.intBitsToFloat(t.rawBuffer[i + 4]);
            
            out.writeFloat(u);
            out.writeFloat(v);
            
            int brightness = t.rawBuffer[i + 7];
            out.writeInt(brightness);

            int color = t.rawBuffer[i + 5];
            out.writeInt(color);
            
            //System.out.println("[" + vertexI + "] x: " + x + ", y: " + y + " z: " + z + ", u: " + u + ", v: " + v + ", b: " + brightness + ", c: " + color);
            
            i += 8;
        }
    }
    
    private static void simplifyPlane(List<MeshQuad> planeQuads) {
        MeshQuad lastQuad = null;
        // Pass 1: merge quads to create rows
        for(MeshQuad quad : planeQuads) {
            if(lastQuad != null) {
                lastQuad.tryToMerge(quad);
            }
            if(quad.isValid(quad)) {
                lastQuad = quad;
            }
        }
        
        // Pass 2: merge rows to create rectangles
        // TODO optimize?
        for(int i = 0; i < planeQuads.size(); i++) {
            for(int j = i + 1; j < planeQuads.size(); j++) {
                planeQuads.get(i).tryToMerge(planeQuads.get(j));
            }
        }
    }
    
    private static int countValidQuads(List<MeshQuad> quads) {
        int quadCount = 0;
        for(MeshQuad quad : quads) {
            if(!quad.deleted) {
                quadCount++;
            }
        }
        return quadCount;
    }

    private ByteBuffer createBuffer(List<? extends MeshQuad> quads, int quadCount) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 6 * 7 * 4);
        BufferWriter out = new BufferWriter(buffer);
        
        try {
            for(MeshQuad quad : quads) {
                if(quad.isValid(quad)) {
                    quad.writeToBuffer(out);
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        
        
        buffer.flip();
        return buffer;
    }
    
    void destroy() {
        if(buffer != null) {
            usedRAM -= buffer.limit();
            instances--;
            buffer = null;
            
            if(gpuStatus == Mesh.GPUStatus.SENT) {
                gpuStatus = Mesh.GPUStatus.PENDING_DELETE;
            }
        }
    }
    
    @Override
    public void destroyBuffer() {
        destroy();
    }
    
    public void update() {
    }
    
    // Java is weird.
    public static short readShortAt(DataInputStream in, int offset) {
        try {
            in.reset();
            in.skip(offset);
            return in.readShort();
        } catch(IOException e) {
            return -1;
        }
    }
    
    public static short readShortAt(byte[] data, int offset) {
        return (short)(Byte.toUnsignedInt(data[offset]) << 8 | Byte.toUnsignedInt(data[offset + 1]));
    }
    
    public static int readIntAt(DataInputStream in, int offset) {
        try {
            in.reset();
            in.skip(offset);
            return in.readInt();
        } catch(IOException e) {
            return -1;
        }
    }
    
    public static int readIntAt(byte[] data, int offset) {
        return (int)(Byte.toUnsignedLong(data[offset]) << 24 | Byte.toUnsignedLong(data[offset + 1]) << 16 | Byte.toUnsignedLong(data[offset + 2]) << 8 | Byte.toUnsignedLong(data[offset + 3]));
    }
    
    public int getStride() {
        return (3 * 4 + (flags.hasTexture ? 8 : 0) + (flags.hasBrightness ? 4 : 0) + (flags.hasColor ? 4 : 0) + (flags.hasNormals ? 4 : 0));
    }
    
    static void saveChunks(List<Integer> coords) {
        System.out.println("saving " + (coords.size() / 3) + " cchunks");
        for(int i = 0; i < coords.size(); i += 3) {
            if(i % 300 == 0) {
                System.out.println((i / 3) + " / " + (coords.size() / 3));
            }
            int theX = coords.get(i);
            int theY = coords.get(i + 1);
            int theZ = coords.get(i + 2);
            
            WorldRenderer wr = new WorldRenderer(Minecraft.getMinecraft().theWorld, new ArrayList<TileEntity>(), theX * 16, theY * 16, theZ * 16, 100000);
    /*
            if (this.occlusionEnabled)
            {
                this.worldRenderers[(var6 * this.renderChunksTall + var5) * this.renderChunksWide + var4].glOcclusionQuery = this.glOcclusionQueryBase.get(var3);
            }*/

            wr.isWaitingOnOcclusionQuery = false;
            wr.isVisible = true;
            wr.isInFrustum = true;
            wr.chunkIndex = 0;
            wr.markDirty();
            wr.updateRenderer(Minecraft.getMinecraft().thePlayer);
        }
        //Tessellator.endSave();
    }
    
    static List<ChunkMesh> getChunkMesh(int theX, int theY, int theZ) {
        WorldRenderer wr = new WorldRenderer(Minecraft.getMinecraft().theWorld, new ArrayList<TileEntity>(), theX * 16, theY * 16, theZ * 16, 100000);
    
        wr.isWaitingOnOcclusionQuery = false;
        wr.isVisible = true;
        wr.isInFrustum = true;
        wr.chunkIndex = 0;
        wr.markDirty();
        wr.updateRenderer(Minecraft.getMinecraft().thePlayer);
        return ((IWorldRenderer)wr).getChunkMeshes();
    }
    
    public double distSq(Entity player) {
        int centerX = x * 16 + 8;
        int centerY = y * 16 + 8;
        int centerZ = z * 16 + 8;
        
        return player.getDistanceSq(centerX, centerY, centerZ); 
    }
    
    public static class Flags {
        boolean hasTexture;
        boolean hasBrightness;
        boolean hasColor;
        boolean hasNormals;
        
        public Flags(byte flags) {
            hasTexture = (flags & 1) != 0;
            hasBrightness = (flags & 2) != 0;
            hasColor = (flags & 4) != 0;
            hasNormals = (flags & 8) != 0;
        }
        
        public Flags(boolean hasTexture, boolean hasBrightness, boolean hasColor, boolean hasNormals) {
            this.hasTexture = hasTexture;
            this.hasBrightness = hasBrightness;
            this.hasColor = hasColor;
            this.hasNormals = hasNormals;
        }
        
        public byte toByte() {
            byte flags = 0;
            if(hasTexture) {
                flags |= 1;
            }
            if(hasBrightness) {
                flags |= 2;
            }
            if(hasColor) {
                flags |= 4;
            }
            if(hasNormals) {
                flags |= 8;
            }
            return flags;
        }
    }
    
}

