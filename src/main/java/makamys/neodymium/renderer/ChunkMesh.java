package makamys.neodymium.renderer;

import static makamys.neodymium.Constants.LOGGER;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import gnu.trove.list.array.TIntArrayList;
import lombok.val;
import makamys.neodymium.Neodymium;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.NeodymiumWorldRenderer;
import makamys.neodymium.util.BufferWriter;
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
    
    public static final AtomicLong usedRAM = new AtomicLong();
    public static final AtomicInteger instances = new AtomicInteger();

    public static final ThreadLocal<QuadMeshBuffer> quadBuf = ThreadLocal.withInitial(QuadMeshBuffer::new);

    private static final QuadNormal[] NORMAL_ORDER = new QuadNormal[] {QuadNormal.NONE, QuadNormal.POSITIVE_Y, QuadNormal.POSITIVE_X, QuadNormal.POSITIVE_Z, QuadNormal.NEGATIVE_X, QuadNormal.NEGATIVE_Z, QuadNormal.NEGATIVE_Y};
    private static final int[] QUAD_NORMAL_TO_NORMAL_ORDER;
    private static final int[] NORMAL_ORDER_TO_QUAD_NORMAL;

    private static final Flags FLAGS = new Flags(true, true, true, false);
    
    static {
        QUAD_NORMAL_TO_NORMAL_ORDER = new int[QuadNormal.values().length];
        NORMAL_ORDER_TO_QUAD_NORMAL = new int[QuadNormal.values().length];
        for(int i = 0; i < QuadNormal.values().length; i++) {
            int idx = Arrays.asList(NORMAL_ORDER).indexOf(QuadNormal.values()[i]);
            if(idx == -1) {
                idx = 0;
            }
            QUAD_NORMAL_TO_NORMAL_ORDER[i] = idx;
            NORMAL_ORDER_TO_QUAD_NORMAL[idx] = i;
        }
    }
    
    public ChunkMesh(WorldRenderer wr, int pass) {
        this.x = wr.posX / 16;
        this.y = wr.posY / 16;
        this.z = wr.posZ / 16;
        this.wr = wr;
        this.pass = pass;
        Arrays.fill(subMeshStart, -1);
        
        instances.getAndIncrement();
        
        if(!quadBuf.get().isEmpty()) {
            LOGGER.error("Invalid state: tried to construct a chunk mesh before the previous one has finished constructing!");
        }
    }

    public void addTessellatorData(Tessellator t) {
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
        // TODO This opengl call crashes the JVM when not run on the client thread.
//        if(t.hasNormals && GL11.glIsEnabled(GL11.GL_LIGHTING)) {
//            errors.add("Chunk uses GL lighting, this is not implemented.");
//        }
        FLAGS.hasBrightness = t.hasBrightness;
        FLAGS.hasColor = t.hasColor;
        
        int verticesPerPrimitive = t.drawMode == GL11.GL_QUADS ? 4 : 3;

        int tessellatorVertexSize = Neodymium.util.vertexSizeInTessellator();
        int quadSize = Neodymium.util.quadSize();

        int quadCount = t.vertexCount / verticesPerPrimitive;

        val buf = quadBuf.get();
        buf.ensureCapacity(quadCount * quadSize);
        for(int quadI = 0; quadI < quadCount; quadI++) {
            boolean deleted = MeshQuad.processQuad(t.rawBuffer, quadI * verticesPerPrimitive * tessellatorVertexSize, buf.data, buf.size, NeoRegion.toRelativeOffset(-t.xOffset), NeoRegion.toRelativeOffset(-t.yOffset), NeoRegion.toRelativeOffset(-t.zOffset), t.drawMode, FLAGS);
            if (!deleted) {
                buf.size += quadSize;
            }
        }
        
        if(!buf.isEmpty()) {
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
                        buf.reset();
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

    private int bufferSize = 0;
    @Override
    public int bufferSize() {
        return bufferSize;
    }

    public void finishConstruction() {
        val buf = quadBuf.get();
        quadCount = buf.size / Neodymium.util.quadSize();
        buffer = createBuffer(buf.data);
        bufferSize = buffer.limit();
        usedRAM.getAndAdd(bufferSize);

        buf.reset();
    }

    //Used by FalseTweaks when cancelling a threaded render job
    public static void cancelRendering() {
        val buf = quadBuf.get();
        if (!buf.isEmpty()) {
            buf.reset();
            LOGGER.debug("Cancelled unfinished render pass!");
        }
    }

    private static final ThreadLocal<MeshQuadBucketSort> threadBucketer = ThreadLocal.withInitial(
            MeshQuadBucketSort::new);

    private ByteBuffer createBuffer(int[] quads) {
        val stride = Neodymium.renderer.getStride();
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 4 * stride);
        BufferWriter out = new BufferWriter(buffer);
        
        boolean sortByNormals = pass == 0;

        val quadSize = Neodymium.util.quadSize();
        int[] indices = null;
        if(sortByNormals) {
            indices = threadBucketer.get().sort(quads, quadSize, quadCount);
        }

        for (int i = 0; i < quadCount; i++) {
            int index = indices != null ? indices[i] : i;
            int subMeshStartIdx = sortByNormals ? QUAD_NORMAL_TO_NORMAL_ORDER[quads[(index + 1) * quadSize - 1]] : 0;
            if(subMeshStart[subMeshStartIdx] == -1) {
                subMeshStart[subMeshStartIdx] = i;
            }
            Neodymium.util.writeMeshQuadToBuffer(quads, index * quadSize, out, stride);
        }

        
        buffer.flip();
        return buffer;
    }
    
    void destroy() {
        if(buffer != null) {
            usedRAM.getAndAdd(-buffer.limit());
            instances.getAndDecrement();
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
        return ((NeodymiumWorldRenderer)wr).nd$getChunkMeshes();
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

    public static class QuadMeshBuffer {
        public int[] data = new int[1024];
        public int size = 0;

        public void ensureCapacity(int maxNewAmount) {
            int newSize = size + maxNewAmount;
            if (newSize > data.length) {
                data = Arrays.copyOf(data, newSize);
            }
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public void reset() {
            size = 0;
        }
    }

    public static class MeshQuadBucketSort {
        private static final int bucketCount = NORMAL_ORDER_TO_QUAD_NORMAL.length;
        private final TIntArrayList[] buckets;
        private int[] resultBuffer;

        public MeshQuadBucketSort() {
            buckets = new TIntArrayList[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                buckets[i] = new TIntArrayList();
            }
        }

        private static int bucket(int[] buffer, int quadSize, int index) {
            return buffer[quadSize * (index + 1) - 1];
        }

        public int[] sort(int[] buffer, int quadSize, int quadCount) {
            for (int i = 0; i < bucketCount; i++) {
                buckets[i].resetQuick();
            }
            for (int i = 0; i < quadCount; i++) {
                buckets[bucket(buffer, quadSize, i)].add(i);
            }
            if (resultBuffer == null || resultBuffer.length < quadCount) {
                resultBuffer = new int[quadCount];
            }
            val result = resultBuffer;
            int offset = 0;
            for (int i = 0; i < bucketCount; i++) {
                val bucket = buckets[NORMAL_ORDER_TO_QUAD_NORMAL[i]];
                val size = bucket.size();
                bucket.toArray(result, 0, offset, size);
                offset += size;
            }
            assert offset == quadCount;
            return result;
        }
    }

}

