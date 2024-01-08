package makamys.neodymium.renderer;

import static makamys.neodymium.Constants.LOGGER;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import lombok.val;
import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.RecyclingList;
import makamys.neodymium.util.Util;
import makamys.neodymium.util.WarningHelper;
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
        List<String> warnings = new ArrayList<>();
        if(t.drawMode != GL11.GL_QUADS && t.drawMode != GL11.GL_TRIANGLES) {
            errors.add("Unsupported draw mode: " + t.drawMode);
        }
        if(!t.hasTexture) {
            errors.add(String.format("Texture data is missing."));
        }
        if(!t.hasBrightness) {
            warnings.add("Brightness data is missing");
        }
        if(!t.hasColor) {
            warnings.add("Color data is missing");
        }
        if(t.hasNormals && GL11.glIsEnabled(GL11.GL_LIGHTING)) {
            errors.add("Chunk uses GL lighting, this is not implemented.");
        }
        FLAGS.hasBrightness = t.hasBrightness;
        FLAGS.hasColor = t.hasColor;
        
        int verticesPerPrimitive = t.drawMode == GL11.GL_QUADS ? 4 : 3;

        int tessellatorVertexSize = 8;
        if (Compat.isOptiFineShadersEnabled())
            tessellatorVertexSize += 10;
        if (Compat.isRPLEModPresent())
            tessellatorVertexSize += 4;

        for(int quadI = 0; quadI < t.vertexCount / verticesPerPrimitive; quadI++) {
            MeshQuad quad = quadBuf.next();
            quad.setState(t.rawBuffer, tessellatorVertexSize, quadI * (verticesPerPrimitive * tessellatorVertexSize), FLAGS, t.drawMode, NeoRegion.toRelativeOffset(-t.xOffset), NeoRegion.toRelativeOffset(-t.yOffset), NeoRegion.toRelativeOffset(-t.zOffset));
            if(quad.deleted) {
                quadBuf.remove();
            }
        }
        
        if(!quadBuf.isEmpty()) {
            // Only show errors if we're actually supposed to be drawing something
            if(!errors.isEmpty() || !warnings.isEmpty()) {
                if(!Config.silenceErrors) {
                    String dimId = wr.worldObj != null && wr.worldObj.provider != null ? "" + wr.worldObj.provider.dimensionId : "UNKNOWN";
                    if(!errors.isEmpty()) {
                        LOGGER.error("Errors in chunk ({}, {}, {}) in dimension {}:", x, y, z, dimId);
                        for(String error : errors) {
                            LOGGER.error("Error: " + error);
                        }
                        for(String warning : warnings) {
                            LOGGER.error("Warning: " + warning);
                        }
                        LOGGER.error("(World renderer pos: ({}, {}, {}), Tessellator pos: ({}, {}, {}), Tessellation count: {}", wr.posX, wr.posY, wr.posZ, t.xOffset, t.yOffset, t.zOffset, tesselatorDataCount);
                        LOGGER.error("Stack trace:");
                        try {
                            // Generate a stack trace
                            throw new IllegalArgumentException();
                        } catch(IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                        LOGGER.error("Skipping chunk due to errors.");
                        quadBuf.reset();
                    } else {
                        WarningHelper.showDebugMessageOnce(String.format("Warnings in chunk (%d, %d, %d) in dimension %s: %s", x, y, z, dimId, String.join(", ", warnings)));
                    }
                }
            }
        }
    }
    
    private static String tessellatorToString(Tessellator t) {
        return "(" + t.xOffset + ", " + t.yOffset + ", " + t.zOffset + ")";
    }
    
    public void finishConstruction() {
        List<MeshQuad> quads = quadBuf.getAsList();

        quadCount = countValidQuads(quads);
        buffer = createBuffer(quads, quadCount);
        usedRAM += buffer.limit();
        
        quadBuf.reset();
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
        val stride = Neodymium.renderer.getStride();
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 4 * stride);
        BufferWriter out = new BufferWriter(buffer);
        
        boolean sortByNormals = pass == 0;
        
        if(sortByNormals) {
            quads.sort(MESH_QUAD_RENDER_COMPARATOR);
        }
        
        int i = 0;
        for(MeshQuad quad : quads) {
            if(i < quadCount) {
                if(MeshQuad.isValid(quad)) {
                    int subMeshStartIdx = sortByNormals ? QUAD_NORMAL_TO_NORMAL_ORDER[quad.normal.ordinal()] : 0;
                    if(subMeshStart[subMeshStartIdx] == -1) {
                        subMeshStart[subMeshStartIdx] = i;
                    }
                    Neodymium.util.writeMeshQuadToBuffer(quad, out, stride);
                    i++;
                } else if(sortByNormals){
                    break;
                }
            }
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
        public boolean hasTexture;
        public boolean hasBrightness;
        public boolean hasColor;
        public boolean hasNormals;
        
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

