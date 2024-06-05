package makamys.neodymium.renderer;

import lombok.val;
import lombok.var;
import makamys.neodymium.Neodymium;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.util.GuiHelper;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static makamys.neodymium.config.Config.bufferSizePass0;
import static makamys.neodymium.config.Config.bufferSizePass1;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

/** Manages dynamic memory allocation inside a buffer on the GPU. */
public class GPUMemoryManager {
    private static final long MEGABYTE = 1024 * 1024L;
    private static final long[] BUFFER_SIZE_BYTES = {
            bufferSizePass0 * MEGABYTE,
            bufferSizePass1 * MEGABYTE
    };

    private static final int INDEX_ALLOCATION_SIZE_BYTES = 512 * 4;

    private static final long USED_VRAM_UPDATE_RATE = 1_000_000_000L;

    private static int copyBuffer = GL_ZERO;
    private static long copyBufferSize = 0;

    private final List<Mesh> sentMeshes = new ArrayList<>();

    private final long bufferSizeBytes;

    public final int managerIndex;
    public final int pass;

    private int indexSize;

    private int nextMesh = 0;

    private long usedVRAM = 0;
    private long lastUsedVRAMUpdate = 0;

    public int VAO = GL_ZERO;
    public int VBO = GL_ZERO;

    public IntBuffer piFirst = null;
    public IntBuffer piCount = null;

    public GPUMemoryManager(int managerIndex, int pass) throws Exception {
        this.bufferSizeBytes = BUFFER_SIZE_BYTES[pass];

        this.managerIndex = managerIndex;
        this.pass = pass;

        try {
            this.VBO = createVBO(bufferSizeBytes);
            this.VAO = createVAO();
        } catch (Exception e) {
            destroyImpl();
            throw e;
        }

        this.indexSize = INDEX_ALLOCATION_SIZE_BYTES;

        reAllocIndexBuffers();
        piFirst.flip();
        piCount.flip();
    }

    public boolean uploadMesh(Mesh mesh) {
        if(mesh == null || mesh.buffer == null)
            return false;

        if(end() + mesh.bufferSize() >= bufferSizeBytes)
            return false;

        val size = mesh.bufferSize();
        var insertIndex = -1;

        var nextBase = -1L;
        if(!sentMeshes.isEmpty()) {
            if(nextMesh < sentMeshes.size() - 1) {
                val meshA = sentMeshes.get(nextMesh);
                Mesh meshB = null;
                for(var i = nextMesh + 1; i < sentMeshes.size(); i++) {
                    val meshC = sentMeshes.get(i);
                    if(meshC.gpuStatus == Mesh.GPUStatus.SENT) {
                        meshB = meshC;
                        break;
                    }
                }
                if(meshB != null && meshB.offset - meshA.getEnd() >= size) {
                    nextBase = meshA.getEnd();
                    insertIndex = nextMesh + 1;
                }
            }

            if(nextBase == -1)
                nextBase = sentMeshes.get(sentMeshes.size() - 1).getEnd();
        }
        if (nextBase == -1)
            nextBase = 0;

        if (mesh.gpuStatus == GPUStatus.UNSENT) {
            uploadMeshToVBO(mesh, nextBase);

            if (insertIndex == -1) {
                sentMeshes.add(mesh);
            } else {
                sentMeshes.add(insertIndex, mesh);
                nextMesh = insertIndex;
            }
        }

        mesh.gpuStatus = GPUStatus.SENT;
        mesh.attachedManager = this;
        return true;
    }

    public void deleteMesh(Mesh mesh) {
        if(mesh == null || mesh.gpuStatus == GPUStatus.UNSENT)
            return;
        mesh.gpuStatus = GPUStatus.PENDING_DELETE;
        mesh.attachedManager = null;
    }

    public void growIndexBuffers() {
        indexSize *= 1.5F;

        reAllocIndexBuffers();

        piFirst.limit(piFirst.capacity());
        piCount.limit(piCount.capacity());
    }

    public void runGC(boolean full) {
        var moved = 0;
        var timesReachedEnd = 0;
        var checksLeft = sentMeshes.size();

        while ((!full && moved < 32 && checksLeft > 0) || full && timesReachedEnd < 2 && !sentMeshes.isEmpty()) {
            checksLeft--;
            nextMesh++;
            if (nextMesh >= sentMeshes.size()) {
                nextMesh = 0;
                timesReachedEnd++;
            }
            val mesh = sentMeshes.get(nextMesh);

            if (mesh.gpuStatus == GPUStatus.SENT) {
                val offset = nextMesh == 0 ? 0 : sentMeshes.get(nextMesh - 1).getEnd();
                if (mesh.offset != offset) {
                    moveMeshInVBO(mesh, offset);
                    moved++;
                }
            } else if (mesh.gpuStatus == GPUStatus.PENDING_DELETE) {
                deleteMeshFromVBO(mesh);
                sentMeshes.remove(nextMesh);
                if (nextMesh > 0)
                    nextMesh--;
            }
        }
    }

    public List<String> debugText() {
        final long t = System.nanoTime();
        if(t - lastUsedVRAMUpdate > USED_VRAM_UPDATE_RATE) {
            usedVRAM = 0;
            for(Mesh mesh : sentMeshes) {
                usedVRAM += mesh.bufferSize();
            }
            lastUsedVRAMUpdate = t;
        }
        return Collections.singletonList("PASS " + pass + ": " + (usedVRAM / 1024 / 1024) + "MB (" + (end() / 1024 / 1024) + "MB) / " + (bufferSizeBytes / 1024 / 1024) + "MB");
    }

    public int drawDebugInfo(int yOff) {
        int scale = 10000;
        int rowLength = 512;

        int height = (int)(bufferSizeBytes / scale) / rowLength;
        GuiHelper.drawRectangle(0, yOff, rowLength, height, 0x000000, 50);

        int meshI = 0;
        for(Mesh mesh : sentMeshes) {

            int o = (int)(mesh.offset / 10000);
            int o2 = (int)((mesh.offset + mesh.bufferSize()) / 10000);
            if(o / rowLength == o2 / rowLength) {
                if(mesh.gpuStatus != Mesh.GPUStatus.PENDING_DELETE) {
                    GuiHelper.drawRectangle(o % rowLength, o / rowLength + yOff, mesh.buffer.limit() / scale + 1, 1, meshI == nextMesh ? 0x00FF00 : 0xFFFFFF);
                }
            } else {
                for(int i = o; i < o2; i++) {
                    int x = i % rowLength;
                    int y = i / rowLength;
                    if(mesh.gpuStatus != Mesh.GPUStatus.PENDING_DELETE) {
                        GuiHelper.drawRectangle(x, y + yOff, 1, 1, 0xFFFFFF);
                    }
                }
            }
            meshI++;
        }
        GuiHelper.drawRectangle(0 % rowLength, 0 + yOff, 4, 4, 0x00FF00);
        GuiHelper.drawRectangle((int)(bufferSizeBytes / scale) % rowLength, (int)(bufferSizeBytes / scale) / rowLength + yOff, 4, 4, 0xFF0000);
        return (int)(bufferSizeBytes / scale) / rowLength + yOff;
    }

    public void destroy() {
        destroyCopyBuffer();

        if (Neodymium.renderer != null) {
            NeoRenderer.submitTask(this::destroyImpl, 60);
        } else {
            destroyImpl();
        }
    }

    private void uploadMeshToVBO(Mesh mesh, long offset) {
        mesh.prepareBuffer();
        if (mesh.bufferSize() > 0)
            copyBytesToVBO(offset, mesh.buffer);

        mesh.iFirst = (int) (offset / Neodymium.renderer.getStride());
        mesh.iCount = mesh.quadCount * 4;
        mesh.offset = offset;
    }

    private void deleteMeshFromVBO(Mesh mesh) {
        deleteBytesFromVBO(mesh.offset, mesh.bufferSize());
        mesh.iFirst = -1;
        mesh.offset = -1;
        mesh.visible = false;
        mesh.gpuStatus = GPUStatus.UNSENT;
        mesh.destroyBuffer();
    }

    private void reAllocIndexBuffers() {
        piFirst = refreshIntBuffer(piFirst, BufferUtils.createByteBuffer(indexSize * 4).asIntBuffer());
        piCount = refreshIntBuffer(piCount, BufferUtils.createByteBuffer(indexSize * 4).asIntBuffer());
    }

    private void moveMeshInVBO(Mesh mesh, long newOffset) {
        moveBytesInVBO(mesh.offset, newOffset, mesh.bufferSize());
        mesh.iFirst = (int) (newOffset / Neodymium.renderer.getStride());
        mesh.offset = newOffset;
    }

    private void copyBytesToVBO(long offset, ByteBuffer bytes) {
        if (bytes.remaining() == 0)
            return;

        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        glBufferSubData(GL_ARRAY_BUFFER, offset, bytes);
        glBindBuffer(GL_ARRAY_BUFFER, GL_ZERO);
    }
    
    private void moveBytesInVBO(long sourceOffsetBytes, long targetOffsetBytes, long sizeBytes) {
        if (sizeBytes == 0)
            return;

        val targetEndBytes = targetOffsetBytes + sizeBytes;
        val sourceEndBytes = sourceOffsetBytes + sizeBytes;

        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        if (sourceOffsetBytes <= targetEndBytes && sourceEndBytes >= targetOffsetBytes) {
            prepareCopyBuffer(sizeBytes);
                    
            glBindBuffer(GL_COPY_WRITE_BUFFER, copyBuffer);
            
            glCopyBufferSubData(GL_ARRAY_BUFFER, GL_COPY_WRITE_BUFFER, sourceOffsetBytes, 0, sizeBytes);
            glCopyBufferSubData(GL_COPY_WRITE_BUFFER, GL_ARRAY_BUFFER, 0, targetOffsetBytes, sizeBytes);
            glBindBuffer(GL_COPY_WRITE_BUFFER, GL_ZERO);
        } else {
            glCopyBufferSubData(GL_ARRAY_BUFFER, GL_ARRAY_BUFFER, sourceOffsetBytes, targetOffsetBytes, sizeBytes);
        }
        glBindBuffer(GL_ARRAY_BUFFER, GL_ZERO);
    }

    private void deleteBytesFromVBO(long offsetBytes, long sizeBytes) {
        // NO-OP
    }

    private long end() {
        return sentMeshes.isEmpty() ? 0 : sentMeshes.get(sentMeshes.size() - 1).getEnd();
    }

    private void destroyImpl() {
        if (VAO != GL_ZERO) {
            glDeleteVertexArrays(VAO);
            VAO = GL_ZERO;
        }
        if (VBO != GL_ZERO) {
            glDeleteBuffers(VBO);
            VBO = GL_ZERO;
        }
    }

    private static int createVBO(long sizeBytes) throws Exception {
        flushGLError();

        val vbo = glGenBuffers();
        if (vbo == GL_ZERO)
            throw new Exception("Failed to create new VBO");

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeBytes, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, GL_ZERO);

        if (checkGLError()) {
            glDeleteBuffers(vbo);
            throw new Exception("Failed to allocate " + sizeBytes + " bytes for new VBO");
        }

        return vbo;
    }

    private static int createVAO() throws Exception {
        val vao = glGenVertexArrays();
        if (vao == GL_ZERO)
            throw new Exception("Failed to create vertex array");
        return vao;
    }

    private static void flushGLError() {
        while (glGetError() != GL_NO_ERROR) ;
    }

    private static boolean checkGLError() {
        return glGetError() != GL_NO_ERROR;
    }

    private static IntBuffer refreshIntBuffer(IntBuffer oldBuf, IntBuffer newBuf) {
        if (oldBuf != null) {
            newBuf.position(oldBuf.position());
        }
        return newBuf;
    }

    private static void prepareCopyBuffer(long requiredSizeBytes) {
        if (copyBufferSize >= requiredSizeBytes)
            return;

        if (copyBuffer == GL_ZERO)
            copyBuffer = glGenBuffers();
        copyBufferSize = next16Megabyte(requiredSizeBytes);

        glBindBuffer(GL_COPY_WRITE_BUFFER, copyBuffer);
        glBufferData(GL_COPY_WRITE_BUFFER, copyBufferSize, GL_DYNAMIC_COPY);
        glBindBuffer(GL_COPY_WRITE_BUFFER, GL_ZERO);
    }

    private static void destroyCopyBuffer() {
        if (copyBuffer == GL_ZERO)
            return;

        glDeleteBuffers(copyBuffer);
        copyBuffer = GL_ZERO;
        copyBufferSize = 0;
    }

    private static long next16Megabyte(long size) {
        val sixteenMegs = 16 * MEGABYTE;
        val increments = size / sixteenMegs + 1;
        return increments * sixteenMegs;
    }
}
