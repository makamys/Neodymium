package makamys.neodymium.renderer;

import static org.lwjgl.opengl.GL15.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.config.Config;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.util.GuiHelper;
import makamys.neodymium.util.ChatUtil;

import static makamys.neodymium.Constants.LOGGER;

/** Manages dynamic memory allocation inside a fixed buffer on the GPU. */
public class GPUMemoryManager {
    
    private long bufferSize;
    
    public int VBO;
    
    private int nextMesh;
    
    private List<Mesh> sentMeshes = new ArrayList<>();
    
    private long usedVRAM;
    private long lastUsedVRAMUpdate;
    private static final long USED_VRAM_UPDATE_RATE = 1_000_000_000L;
    
    private static final long MAX_VRAM_FULLNESS_INTERVAL = 30L * 1_000_000_000L;
    private static long lastVRAMFullness = -1;
    
    public GPUMemoryManager() {
        VBO = glGenBuffers();
        
        bufferSize = ((long)Config.VRAMSize) * 1024 * 1024;
        
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        
        glBufferData(GL_ARRAY_BUFFER, bufferSize, GL_DYNAMIC_DRAW);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    public void runGC(boolean full) {
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        
        int moved = 0;
        int timesReachedEnd = 0;
        int checksLeft = sentMeshes.size();
        
        final int MB256 = 256 * 1024 * 1024;
        
        int panicRate = (int)(((float)Math.max(MB256 - (bufferSize - end()), 0) / (float)MB256) * 64f);
        
        while((!full && (moved < (4 + panicRate) && checksLeft-- > 0)) || (full && timesReachedEnd < 2) && !sentMeshes.isEmpty()) {
            nextMesh++;
            if(nextMesh >= sentMeshes.size()) {
                nextMesh = 0;
                timesReachedEnd++;
            }
            Mesh mesh = sentMeshes.get(nextMesh);
            
            if(mesh.gpuStatus == GPUStatus.SENT) {
                long offset = nextMesh == 0 ? 0 : sentMeshes.get(nextMesh - 1).getEnd();
                if(mesh.offset != offset) {
                    glBufferSubData(GL_ARRAY_BUFFER, offset, mesh.buffer);
                    moved++;
                }
                mesh.iFirst = (int)(offset / Neodymium.renderer.getStride());
                mesh.offset = offset;
            } else if(mesh.gpuStatus == GPUStatus.PENDING_DELETE) {
                mesh.iFirst = -1;
                mesh.offset = -1;
                mesh.visible = false;
                mesh.gpuStatus = GPUStatus.UNSENT;
                
                sentMeshes.remove(nextMesh);
                
                mesh.destroyBuffer();
                
                if(nextMesh > 0) {
                    nextMesh--;
                }
            }
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    private long end() {
        return (sentMeshes.isEmpty() ? 0 : sentMeshes.get(sentMeshes.size() - 1).getEnd());
    }
    
    public void sendMeshToGPU(Mesh mesh) {
        if(mesh == null || mesh.buffer == null) {
            return;
        }
        
        if(end() + mesh.bufferSize() >= bufferSize) {
            runGC(true);
        }
        
        if(end() + mesh.bufferSize() >= bufferSize) {
            Neodymium.renderer.destroyPending = true;
            
            long t = System.nanoTime();
            
            if(lastVRAMFullness != -1 && t - lastVRAMFullness < MAX_VRAM_FULLNESS_INTERVAL) {
                ChatUtil.showNeoChatMessage("VRAM keeps getting full! Reverting to vanilla renderer. Try increasing the VRAM buffer size in the config, if possible.", ChatUtil.MessageVerbosity.ERROR, false);
                Compat.onNotEnoughVRAM(Config.VRAMSize);
            } else {
                LOGGER.debug("Reloading renderer because VRAM is full.");
                // TODO restart renderer with more VRAM allocated when this happens.
            }
            lastVRAMFullness = t;
            return;
        }
        
        int size = mesh.bufferSize();
        int insertIndex = -1;
        
        long nextBase = -1;
        if(!sentMeshes.isEmpty()) {
            if(nextMesh < sentMeshes.size() - 1) {
                Mesh next = sentMeshes.get(nextMesh);
                Mesh nextnext = null;
                for(int i = nextMesh + 1; i < sentMeshes.size(); i++) {
                    Mesh m = sentMeshes.get(i);
                    if(m.gpuStatus == Mesh.GPUStatus.SENT) {
                        nextnext = m;
                        break;
                    }
                }
                if(nextnext != null && nextnext.offset - next.getEnd() >= size) {
                    nextBase = next.getEnd();
                    insertIndex = nextMesh + 1;
                }
            }
            
            if(nextBase == -1) {
                nextBase = sentMeshes.get(sentMeshes.size() - 1).getEnd();
            }
        }
        if(nextBase == -1) nextBase = 0;
        
        
        if(mesh.gpuStatus == GPUStatus.UNSENT) {
            mesh.prepareBuffer();
            
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
            glBufferSubData(GL_ARRAY_BUFFER, nextBase, mesh.buffer);
            mesh.iFirst = (int)(nextBase / Neodymium.renderer.getStride());
            mesh.iCount = mesh.quadCount * 4;
            mesh.offset = nextBase;
            
            if(insertIndex == -1) {
                sentMeshes.add(mesh);
            } else {
                sentMeshes.add(insertIndex, mesh);
                nextMesh = insertIndex;
            }
            
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
        long t = System.nanoTime();
        if(t - lastUsedVRAMUpdate > USED_VRAM_UPDATE_RATE) {
            usedVRAM = 0;
            for(Mesh mesh : sentMeshes) {
                usedVRAM += mesh.bufferSize();
            }
            lastUsedVRAMUpdate = t;
        }
        return Arrays.asList("VRAM: " + (usedVRAM / 1024 / 1024) + "MB (" + (end() / 1024 / 1024) + "MB) / " + (bufferSize / 1024 / 1024) + "MB");
    }

    public void drawInfo() {
        int scale = 10000;
        int rowLength = 512;
        int yOff = 20;
        
        int height = (int)(bufferSize / scale) / rowLength;
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
        GuiHelper.drawRectangle((int)(bufferSize / scale) % rowLength, (int)(bufferSize / scale) / rowLength + yOff, 4, 4, 0xFF0000);
    }
    
    public float getCoherenceRate() {
        return (float)ChunkMesh.usedRAM / (float)end();
    }
    
}
