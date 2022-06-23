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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.BufferUtils;

import makamys.neodymium.Config;
import makamys.neodymium.MixinConfigPlugin;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.RecyclingList;
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
    
    private int[] subMeshStart = new int[NORMAL_ORDER.length]; 
    
 // TODO move this somewhere else
    List<String> nameList = (List<String>) ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites.keySet().stream().collect(Collectors.toList());
    
    public static int usedRAM = 0;
    public static int instances = 0;
    
    private static RecyclingList<MeshQuad> quadBuf = new RecyclingList<>(() -> new MeshQuad());
    
    private static final QuadNormal[] NORMAL_ORDER = new QuadNormal[] {QuadNormal.NONE, QuadNormal.POSITIVE_Y, QuadNormal.POSITIVE_X, QuadNormal.POSITIVE_Z, QuadNormal.NEGATIVE_X, QuadNormal.NEGATIVE_Z, QuadNormal.NEGATIVE_Y};
    private static final Comparator<MeshQuad> MESH_QUAD_RENDER_COMPARATOR = new MeshQuadRenderOrderComparator();
    private static final int[] QUAD_NORMAL_TO_NORMAL_ORDER;
    
    static {
        QUAD_NORMAL_TO_NORMAL_ORDER = new int[QuadNormal.values().length];
        for(int i = 0; i < QuadNormal.values().length; i++) {
            int idx = Arrays.asList(NORMAL_ORDER).indexOf(QuadNormal.values()[i]);
            if(idx == -1) {
                idx = 0;
            }
            QUAD_NORMAL_TO_NORMAL_ORDER[i] = idx;
        }
    }
    
    public ChunkMesh(int x, int y, int z, Flags flags, int quadCount, List<MeshQuad> quads, int pass) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.flags = flags;
        this.quadCount = quadCount;
        this.pass = pass;
        Arrays.fill(subMeshStart, -1);
        
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
        int tessellatorXOffset = fr ? 0 : xOffset;
        int tessellatorYOffset = fr ? 0 : yOffset;
        int tessellatorZOffset = fr ? 0 : zOffset;
        
        ChunkMesh.Flags flags = new ChunkMesh.Flags(t.hasTexture, t.hasBrightness, t.hasColor, t.hasNormals);
        
        quadBuf.reset();
        
        for(int quadI = 0; quadI < t.vertexCount / 4; quadI++) {
            quadBuf.next().setState(t.rawBuffer, quadI * 32, flags, tessellatorXOffset, tessellatorYOffset, tessellatorZOffset);
        }
        
        List<MeshQuad> quads = quadBuf.getAsList();
        
        if(Config.simplifyChunkMeshes) {
            ArrayList<ArrayList<MeshQuad>> quadsByPlaneDir = new ArrayList<>(); // XY, XZ, YZ
            for(int i = 0; i < 3; i++) {
                quadsByPlaneDir.add(new ArrayList<MeshQuad>());
            }
            for(MeshQuad quad : quads) {
                if(quad.getPlane() != MeshQuad.Plane.NONE) {
                    quadsByPlaneDir.get(quad.getPlane().ordinal() - 1).add(quad);
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
                        simplifyPlane(planeDirQuads.subList(planeStart, quadI + 1));
                        planeStart = quadI + 1;
                    }
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
    }
    
    private static void simplifyPlane(List<MeshQuad> planeQuads) {
        // Exclude quads from merging if they have identical vertex positions to another quad.
        // Workaround for z-fighting issue that arises when merging fancy grass and the overlay quad
        // is a different dimension than the base quad.
        for(int i = 0; i < planeQuads.size(); i++) {
            MeshQuad a = planeQuads.get(i);
            for(int j = i + 1; j < planeQuads.size(); j++) {
                MeshQuad b = planeQuads.get(j);
                if(!a.noMerge && a.isPosEqual(b)) {
                    a.noMerge = true;
                    b.noMerge = true;
                } else {
                    // Due to sorting, identical quads will always be next to each other
                    break;
                }
            }
        }
        
        MeshQuad lastQuad = null;
        // Pass 1: merge quads to create rows
        for(MeshQuad quad : planeQuads) {
            if(lastQuad != null) {
                lastQuad.tryToMerge(quad);
            }
            if(MeshQuad.isValid(quad)) {
                lastQuad = quad;
            }
        }
        
        for(int i = 0; i < planeQuads.size(); i++) {
            planeQuads.get(i).mergeReference = null;
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
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 4 * MeshQuad.getStride());
        BufferWriter out = new BufferWriter(buffer);
        
        quads.sort(MESH_QUAD_RENDER_COMPARATOR);
        
        try {
            int i = 0;
            for(MeshQuad quad : quads) {
                if(i < quadCount) {
                    if(MeshQuad.isValid(quad)) {
                        int subMeshStartIdx = QUAD_NORMAL_TO_NORMAL_ORDER[quad.normal.ordinal()];
                        if(subMeshStart[subMeshStartIdx] == -1) {
                            subMeshStart[subMeshStartIdx] = i;
                        }
                        quad.writeToBuffer(out);
                        i++;
                    } else {
                        break;
                    }
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
        return MeshQuad.getStride();
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
    
    @Override
    public void writeToIndexBuffer(IntBuffer piFirst, IntBuffer piCount, int[] renderedMeshesReturn,
            int[] renderedQuadsReturn, int cameraXDiv, int cameraYDiv, int cameraZDiv) {
        if(!Config.cullFaces) {
            super.writeToIndexBuffer(piFirst, piCount, renderedMeshesReturn, renderedQuadsReturn, cameraXDiv, cameraYDiv, cameraZDiv);
            return;
        }
        
        renderedMeshesReturn[0] = 0;
        renderedQuadsReturn[0] = 0;
        
        int startIndex = -1;
        for(int i = 0; i < NORMAL_ORDER.length + 1; i++) {
            if(i < subMeshStart.length && subMeshStart[i] == -1) continue;
            
            QuadNormal normal = i < NORMAL_ORDER.length ? NORMAL_ORDER[i] : null;
            boolean isVisible = normal != null && isNormalVisible(normal, cameraXDiv, cameraYDiv, cameraZDiv);
            
            if(isVisible && startIndex == -1) {
                startIndex = subMeshStart[QUAD_NORMAL_TO_NORMAL_ORDER[normal.ordinal()]];
            } else if(!isVisible && startIndex != -1) {
                int endIndex = i < subMeshStart.length ? subMeshStart[i] : quadCount;
                
                piFirst.put(iFirst + (startIndex*4));
                piCount.put((endIndex - startIndex)*4);
                renderedMeshesReturn[0]++;
                renderedQuadsReturn[0] += endIndex - startIndex; // TODO remove this, it's redundant
                
                startIndex = -1;
            }
        }
    }
    
    private boolean isNormalVisible(QuadNormal normal, int interpXDiv, int interpYDiv, int interpZDiv) {
        switch(normal) {
        case POSITIVE_X:
            return interpXDiv >= ((x + 0));
        case NEGATIVE_X:
            return interpXDiv < ((x + 1));
        case POSITIVE_Y:
            return interpYDiv >= ((y + 0));
        case NEGATIVE_Y:
            return interpYDiv < ((y + 1));
        case POSITIVE_Z:
            return interpZDiv >= ((z + 0));
        case NEGATIVE_Z:
            return interpZDiv < ((z + 1));
        default:
            return true;
        }
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
    
    private static class MeshQuadRenderOrderComparator implements Comparator<MeshQuad> {

        @Override
        public int compare(MeshQuad a, MeshQuad b) {
            if(!MeshQuad.isValid(b)) {
                return 1;
            } else if(!MeshQuad.isValid(a)) {
                return -1;
            } else {
                return QUAD_NORMAL_TO_NORMAL_ORDER[a.normal.ordinal()] - QUAD_NORMAL_TO_NORMAL_ORDER[b.normal.ordinal()];
            }
        }
        
    }
    
}

