package makamys.lodmod.renderer;

import static org.lwjgl.opengl.GL15.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import makamys.lodmod.renderer.Mesh.GPUStatus;
import makamys.lodmod.util.GuiHelper;

public class GPUMemoryManager {
    
    private static int BUFFER_SIZE = 1024 * 1024 * 1024;
    
    public int VBO;
    
    private int nextMesh;
    
    private List<Mesh> sentMeshes = new ArrayList<>();
    
    public GPUMemoryManager() {
        VBO = glGenBuffers();
        
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        
        glBufferData(GL_ARRAY_BUFFER, BUFFER_SIZE, GL_DYNAMIC_DRAW);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    public void runGC() {
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
        int deletedNum = 0;
        int deletedRAM = 0;
        
        long t0 = System.nanoTime();
        
        int moved = 0;
        
        Mesh startMesh = null;
        
        while(moved < 5 && !sentMeshes.isEmpty()) {
            if(nextMesh >= sentMeshes.size()) {
                nextMesh = 0;
            }
            Mesh mesh = sentMeshes.get(nextMesh);
            
            if(mesh == startMesh) {
                break; // we have wrapped around
            } else if(startMesh == null) {
                startMesh = mesh;
            }
            
            if(mesh.gpuStatus == GPUStatus.SENT) {
                int offset = nextMesh == 0 ? 0 : sentMeshes.get(nextMesh - 1).getEnd();
                if(mesh.offset != offset) {
                    glBufferSubData(GL_ARRAY_BUFFER, offset, mesh.buffer);
                    moved++;
                }
                mesh.iFirst = offset / mesh.getStride();
                mesh.offset = offset;
                nextMesh++;
            } else if(mesh.gpuStatus == GPUStatus.PENDING_DELETE) {
                mesh.iFirst = mesh.offset = -1;
                mesh.visible = false;
                mesh.gpuStatus = GPUStatus.UNSENT;
                mesh.destroyBuffer();
                
                sentMeshes.remove(nextMesh);
                
                deletedNum++;
                deletedRAM += mesh.bufferSize();
            }
        }
        
        long t1 = System.nanoTime();
        
        //System.out.println("Deleted " + deletedNum + " meshes in " + ((t1 - t0) / 1_000_000.0) + " ms, freeing up " + (deletedRAM / 1024 / 1024) + "MB of VRAM");
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    private int malloc(int size) {
        int nextBase = 0;
        if(!sentMeshes.isEmpty()) {
            nextBase = sentMeshes.get(sentMeshes.size() - 1).getEnd();
        }
        
        if(nextBase + size >= BUFFER_SIZE) {
            return -1;
        } else {
            return nextBase;
        }
    }
    
    public void sendMeshToGPU(Mesh mesh) {
        if(mesh == null) {
            return;
        }
        
        int nextMeshOffset = malloc(mesh.buffer.limit());
        
        if(nextMeshOffset == -1) {
            return;
        }
        
        if(mesh.gpuStatus == GPUStatus.UNSENT) {
            mesh.prepareBuffer();
            
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
            glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
            mesh.iFirst = nextMeshOffset / mesh.getStride();
            mesh.iCount = mesh.quadCount * 6;
            mesh.offset = nextMeshOffset;
            
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
        return Arrays.asList("VRAM: " + (malloc(0) / 1024 / 1024) + "MB / " + (BUFFER_SIZE / 1024 / 1024) + "MB");
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
        GuiHelper.drawRectangle((BUFFER_SIZE / scale) % rowLength, (BUFFER_SIZE / scale) / rowLength + yOff, 4, 4, 0xFF0000);
    }
    
}
