package makamys.neodymium.renderer;

import static makamys.neodymium.Constants.LOGGER;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.RecyclingList;
import makamys.neodymium.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

/** A mesh for a 16x16x16 region of the world. */
public class ChunkMesh extends Mesh {
    
    WorldRenderer wr;
    private int tesselatorDataCount;
    
    private int[] subMeshStart = new int[NORMAL_ORDER.length]; 
    
    public static int usedRAM = 0;
    public static int instances = 0;
    
    private static RecyclingList<MeshQuad> quadBuf = new RecyclingList<>(() -> new MeshQuad());
    
    private static ChunkMesh meshCaptureTarget;
    
    private static final QuadNormal[] NORMAL_ORDER = new QuadNormal[] {QuadNormal.NONE, QuadNormal.POSITIVE_Y, QuadNormal.POSITIVE_X, QuadNormal.POSITIVE_Z, QuadNormal.NEGATIVE_X, QuadNormal.NEGATIVE_Z, QuadNormal.NEGATIVE_Y};
    private static final Comparator<MeshQuad> MESH_QUAD_RENDER_COMPARATOR = new MeshQuadRenderOrderComparator();
    private static final int[] QUAD_NORMAL_TO_NORMAL_ORDER;
    
    private static final Flags FLAGS = new Flags(true, true, true, false);
    
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
    
    public ChunkMesh(WorldRenderer wr, int pass) {
        this.x = wr.posX / 16;
        this.y = wr.posY / 16;
        this.z = wr.posZ / 16;
        this.wr = wr;
        this.pass = pass;
        Arrays.fill(subMeshStart, -1);
        
        instances++;
        
        if(!quadBuf.getAsList().isEmpty()) {
            LOGGER.error("Invalid state: tried to construct a chunk mesh before the previous one has finished constructing!");
        }
    }
    
    public static void preTessellatorDraw(Tessellator t) {
        if(meshCaptureTarget != null) {
            meshCaptureTarget.addTessellatorData(t);
        }
    }
    
    private void addTessellatorData(Tessellator t) {
        tesselatorDataCount++;
        
        if(t.vertexCount == 0) {
            // Sometimes the tessellator has no vertices and weird flags. Don't warn in this case, just silently return.
            return;
        }
        List<String> errors = new ArrayList<>();
        if(t.drawMode != GL11.GL_QUADS && t.drawMode != GL11.GL_TRIANGLES) {
            errors.add("Unsupported draw mode: " + t.drawMode);
        }
        if(!t.hasTexture || !t.hasBrightness || !t.hasColor) {
            errors.add(String.format("Unsupported tessellator flags: (hasTexture=%b, hasBrightness=%b, hasColor=%b)", t.hasTexture, t.hasBrightness, t.hasColor));
        }
        if(t.hasNormals && GL11.glIsEnabled(GL11.GL_LIGHTING)) {
            errors.add("Chunk uses GL lighting, this is not implemented.");
        }
        
        int verticesPerPrimitive = t.drawMode == GL11.GL_QUADS ? 4 : 3;
        
        for(int quadI = 0; quadI < t.vertexCount / verticesPerPrimitive; quadI++) {
            MeshQuad quad = quadBuf.next();
            quad.setState(t.rawBuffer, quadI * (verticesPerPrimitive * 8), FLAGS, t.drawMode, (float)-t.xOffset, (float)-t.yOffset, (float)-t.zOffset);
            if(quad.deleted) {
                quadBuf.remove();
            }
        }
        
        if(!quadBuf.isEmpty()) {
            // Only show errors if we're actually supposed to be drawing something
            if(!errors.isEmpty()) {
                if(!Config.silenceErrors) {
                    try {
                        // Generate a stack trace
                        throw new IllegalArgumentException();
                    } catch(IllegalArgumentException e) {
                        LOGGER.error("Errors in chunk ({}, {}, {})", x, y, z);
                        for(String error : errors) {
                            LOGGER.error("Error: " + error);
                        }
                        LOGGER.error("(World renderer pos: ({}, {}, {}), Tessellator pos: ({}, {}, {}), Tessellation count: {}", wr.posX, wr.posY, wr.posZ, t.xOffset, t.yOffset, t.zOffset, tesselatorDataCount);
                        LOGGER.error("Stack trace:");
                        e.printStackTrace();
                        LOGGER.error("Skipping chunk due to errors.");
                    }
                }
                return;
            }
        }
    }
    
    private static String tessellatorToString(Tessellator t) {
        return "(" + t.xOffset + ", " + t.yOffset + ", " + t.zOffset + ")";
    }
    
    public void finishConstruction() {
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
        
        quadCount = countValidQuads(quads);
        buffer = createBuffer(quads, quadCount);
        usedRAM += buffer.limit();
        
        quadBuf.reset();
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
        
        boolean sortByNormals = pass == 0;
        
        if(sortByNormals) {
            quads.sort(MESH_QUAD_RENDER_COMPARATOR);
        }
        
        try {
            int i = 0;
            for(MeshQuad quad : quads) {
                if(i < quadCount) {
                    if(MeshQuad.isValid(quad)) {
                        int subMeshStartIdx = sortByNormals ? QUAD_NORMAL_TO_NORMAL_ORDER[quad.normal.ordinal()] : 0;
                        if(subMeshStart[subMeshStartIdx] == -1) {
                            subMeshStart[subMeshStartIdx] = i;
                        }
                        quad.writeToBuffer(out);
                        i++;
                    } else if(sortByNormals){
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
    
    public int getStride() {
        return MeshQuad.getStride();
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
    public int writeToIndexBuffer(IntBuffer piFirst, IntBuffer piCount, int cameraXDiv, int cameraYDiv, int cameraZDiv, int pass) {
        if(!Config.cullFaces) {
            return super.writeToIndexBuffer(piFirst, piCount, cameraXDiv, cameraYDiv, cameraZDiv, pass);
        }
        
        int renderedMeshes = 0;
        
        int startIndex = -1;
        for(int i = 0; i < NORMAL_ORDER.length + 1; i++) {
            if(i < subMeshStart.length && subMeshStart[i] == -1) continue;
            
            QuadNormal normal = i < NORMAL_ORDER.length ? NORMAL_ORDER[i] : null;
            boolean isVisible = normal != null && isNormalVisible(normal, cameraXDiv, cameraYDiv, cameraZDiv, pass);
            
            if(isVisible && startIndex == -1) {
                startIndex = subMeshStart[QUAD_NORMAL_TO_NORMAL_ORDER[normal.ordinal()]];
            } else if(!isVisible && startIndex != -1) {
                int endIndex = i < subMeshStart.length ? subMeshStart[i] : quadCount;
                
                piFirst.put(iFirst + (startIndex*4));
                piCount.put((endIndex - startIndex)*4);
                renderedMeshes++;
                
                startIndex = -1;
            }
        }
        
        return renderedMeshes;
    }
    
    private boolean isNormalVisible(QuadNormal normal, int interpXDiv, int interpYDiv, int interpZDiv, int pass) {
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
            return pass != 0 || Config.maxUnalignedQuadDistance == Integer.MAX_VALUE
            || Util.distSq(interpXDiv, interpYDiv, interpZDiv, x, y, z) < Math.pow((double)Config.maxUnalignedQuadDistance, 2);
        }
    }
    
    public double distSq(Entity player) {
        int centerX = x * 16 + 8;
        int centerY = y * 16 + 8;
        int centerZ = z * 16 + 8;
        
        return player.getDistanceSq(centerX, centerY, centerZ); 
    }
    
    public static void setCaptureTarget(ChunkMesh cm) {
        meshCaptureTarget = cm;
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
                return -1;
            } else if(!MeshQuad.isValid(a)) {
                return 1;
            } else {
                return QUAD_NORMAL_TO_NORMAL_ORDER[a.normal.ordinal()] - QUAD_NORMAL_TO_NORMAL_ORDER[b.normal.ordinal()];
            }
        }
        
    }

}

