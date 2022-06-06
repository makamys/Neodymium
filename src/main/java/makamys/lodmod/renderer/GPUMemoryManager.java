package makamys.lodmod.renderer;

import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import makamys.lodmod.renderer.Mesh.GPUStatus;
import makamys.lodmod.util.GuiHelper;

public class GPUMemoryManager {
    
    private static int BUFFER_SIZE = 1024 * 1024 * 1024;
    
    public int VBO;
    
    private int nextTri;
    private int nextMeshOffset;
    private int nextMesh;
    
    private List<Mesh> sentMeshes = new ArrayList<>();
    
    public GPUMemoryManager() {
        VBO = glGenBuffers();
        
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        
        glBufferData(GL_ARRAY_BUFFER, BUFFER_SIZE, GL_STATIC_DRAW);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    public void runGC() {
        nextMeshOffset = 0;
        nextTri = 0;
        
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
        int deletedNum = 0;
        int deletedRAM = 0;
        
        long t0 = System.nanoTime();
        
        for(Iterator<Mesh> it = sentMeshes.iterator(); it.hasNext(); ) {
            Mesh mesh = it.next();
            if(mesh.gpuStatus == GPUStatus.SENT) {
                if(mesh.offset != nextMeshOffset) {
                    glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
                }
                mesh.iFirst = nextTri;
                mesh.offset = nextMeshOffset;
                
                nextMeshOffset += mesh.bufferSize();
                nextTri += mesh.quadCount * 6;
            } else if(mesh.gpuStatus == GPUStatus.PENDING_DELETE) {
                mesh.iFirst = mesh.offset = -1;
                mesh.visible = false;
                mesh.gpuStatus = GPUStatus.UNSENT;
                mesh.destroyBuffer();
                
                it.remove();
                deletedNum++;
                deletedRAM += mesh.bufferSize();
            }
        }
        
        long t1 = System.nanoTime();
        
        System.out.println("Deleted " + deletedNum + " meshes in " + ((t1 - t0) / 1_000_000.0) + " ms, freeing up " + (deletedRAM / 1024 / 1024) + "MB of VRAM");
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    public void sendMeshToGPU(Mesh mesh) {
        if(mesh == null) {
            return;
        }
        if(mesh.gpuStatus == GPUStatus.UNSENT) {
            mesh.prepareBuffer();
            
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
            glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
            mesh.iFirst = nextTri;
            mesh.iCount = mesh.quadCount * 6;
            mesh.offset = nextMeshOffset;
            
            nextTri += mesh.quadCount * 6;
            nextMeshOffset += mesh.buffer.limit();
            sentMeshes.add(mesh);
            
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        
        mesh.gpuStatus = GPUStatus.SENT;
    }
    
    public void deleteMeshFromGPU(Mesh mesh) {
        if(mesh == null || mesh.gpuStatus == GPUStatus.UNSENT) {
            return;
        }
        mesh.gpuStatus = GPUStatus.PENDING_DELETE;
    }

    public void destroy() {
        glDeleteBuffers(VBO);
    }

    public List<String> getDebugText() {
        return Arrays.asList("VRAM: " + (nextMeshOffset / 1024 / 1024) + "MB / " + (BUFFER_SIZE / 1024 / 1024) + "MB");
    }

    public void drawInfo() {
        int scale = 10000;
        int rowLength = 512;
        int yOff = 20;
        
        int meshI = 0;
        for(Mesh mesh : sentMeshes) {
            
            int o = mesh.offset / 10000;
            int o2 = (mesh.offset + mesh.buffer.limit()) / 10000;
            if(o / rowLength == o2 / rowLength) {
                if(mesh.gpuStatus != Mesh.GPUStatus.PENDING_DELETE) {
                    GuiHelper.drawRectangle(o % rowLength, o / rowLength + yOff, mesh.buffer.limit() / scale + 1, 1, meshI % 2 == 0 ? 0xFFFFFF : 0x808080);
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
        GuiHelper.drawRectangle((BUFFER_SIZE / scale) % rowLength, (BUFFER_SIZE / scale) / rowLength + yOff, 4, 4, 0xFF0000);
    }
    
}
