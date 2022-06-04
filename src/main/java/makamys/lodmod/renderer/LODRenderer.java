package makamys.lodmod.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.ChunkEvent;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import makamys.lodmod.LODMod;
import makamys.lodmod.ducks.IWorldRenderer;
import makamys.lodmod.renderer.Mesh.GPUStatus;
import makamys.lodmod.util.Util;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class LODRenderer {
    
    public boolean hasInited = false;
    
    private boolean[] wasDown = new boolean[256];
    private int renderQuads = 0;
    
    public boolean renderWorld = true;
    public boolean renderLOD = true;
    
    private static int BUFFER_SIZE = 1024 * 1024 * 1024;
    private static int MAX_MESHES = 100000;
    
    private int VAO, VBO, shaderProgram;
    private IntBuffer[] piFirst = new IntBuffer[2];
    private IntBuffer[] piCount = new IntBuffer[2];
    private List<Mesh>[] sentMeshes = (List<Mesh>[])new ArrayList[] {new ArrayList<Mesh>(), new ArrayList<Mesh>()};
    
    List<Chunk> myChunks = new ArrayList<Chunk>();
    List<LODChunk> pendingLODChunks = new ArrayList<>();
    
    private boolean hasServerInited = false;
    private Map<ChunkCoordIntPair, LODRegion> loadedRegionsMap = new HashMap<>();
    
    public World world;
    
    // TODO make these packets to make this work on dedicated servers
    Queue<Chunk> farChunks = new ConcurrentLinkedQueue<>();
    
    List<ChunkCoordIntPair> serverChunkLoadQueue = new ArrayList<>();
    
    private double lastSortX = Double.NaN;
    private double lastSortY = Double.NaN;
    private double lastSortZ = Double.NaN;
    
    private long lastGCTime = -1;
    private long lastSaveTime = -1;
    private long gcInterval = 10 * 1000;
    private long saveInterval = 60 * 1000;
    
    private int renderedMeshes;
    private int frameCount;
    
    public int renderRange = 48;
    
    private boolean freezeMeshes;
    
    public LODRenderer(World world){
        this.world = world;
        if(shouldRenderInWorld(world)) {
            hasInited = init();
        }
    }
    
    public void preRenderSortedRenderers(int renderPass, double alpha, WorldRenderer[] sortedWorldRenderers) {
        if(renderPass != 0) return;
        
        LODMod.fogEventWasPosted = false;
        
        renderedMeshes = 0;
        
        Minecraft.getMinecraft().entityRenderer.enableLightmap((double)alpha);
        
        if(hasInited) {
            for(WorldRenderer wr : sortedWorldRenderers) {
                if(wr != null) {
                    ((IWorldRenderer)wr).myTick();
                }
            }
            
            mainLoop();
            if(LODMod.debugEnabled) {
                handleKeyboard();
            }
            if(lastGCTime == -1 || (System.currentTimeMillis() - lastGCTime) > gcInterval) {
                runGC();
                lastGCTime = System.currentTimeMillis();
            }
            if(lastSaveTime == -1 || (System.currentTimeMillis() - lastSaveTime) > saveInterval) {
                onSave();
                lastSaveTime = System.currentTimeMillis();
            }
            
            if(renderLOD) {
                if(frameCount % LODMod.sortFrequency == 0) {
                    sort();
                }
                
                updateMeshes();
                initIndexBuffers();
                render(alpha);
            }
        }
        
        Minecraft.getMinecraft().entityRenderer.disableLightmap((double)alpha);
    }
    
    private void sort() {
        Entity player = Minecraft.getMinecraft().renderViewEntity;
        for(List<Mesh> list : sentMeshes) {
            list.sort(new MeshDistanceComparator(player.posX / 16, player.posY / 16, player.posZ / 16));
        }
    }
    
    private void updateMeshes() {
        for(List<Mesh> list : sentMeshes) {
            for(Mesh mesh : list) {
                mesh.update();
            }
        }
    }
    
    private void initIndexBuffers() {
        for(int i = 0; i < 2; i++) {
            piFirst[i].limit(sentMeshes[i].size() + sentMeshes[i].size());
            piCount[i].limit(sentMeshes[i].size() + sentMeshes[i].size());
            for(Mesh mesh : sentMeshes[i]) {
                if(mesh.visible && (LODMod.maxMeshesPerFrame == -1 || renderedMeshes < LODMod.maxMeshesPerFrame)) {
                    renderedMeshes++;
                    piFirst[i].put(mesh.iFirst);
                    piCount[i].put(mesh.iCount);
                }
            }
            piFirst[i].flip();
            piCount[i].flip();
        }
    }
    
    private void mainLoop() {
        while(!farChunks.isEmpty()) {
            LODChunk lodChunk = receiveFarChunk(farChunks.remove());
            sendChunkToGPU(lodChunk);
        }
        
        if(Minecraft.getMinecraft().playerController.netClientHandler.doneLoadingTerrain) {
            Entity player = Minecraft.getMinecraft().renderViewEntity;
            
            List<ChunkCoordIntPair> newServerChunkLoadQueue = new ArrayList<>();
            
            if(Double.isNaN(lastSortX) || getLastSortDistanceSq(player) > 16 * 16) {
                int centerX = (int)Math.floor(player.posX / 16.0);
                int centerZ = (int)Math.floor(player.posZ / 16.0);
                
                for(int x = -renderRange; x <= renderRange; x++) {
                    for(int z = -renderRange; z <= renderRange; z++) {
                        if(x * x + z * z < renderRange * renderRange) {
                            int chunkX = centerX + x;
                            int chunkZ = centerZ + z;
                            
                            if(getLODChunk(chunkX, chunkZ).needsChunk) {
                                newServerChunkLoadQueue.add(new ChunkCoordIntPair(chunkX, chunkZ));
                                getLODChunk(chunkX, chunkZ).needsChunk = false;
                            }
                        }
                    }
                }
                Collections.sort(newServerChunkLoadQueue, new ChunkCoordDistanceComparator(player.posX, player.posY, player.posZ));
                addToServerChunkLoadQueue(newServerChunkLoadQueue);
                
                lastSortX = player.posX;
                lastSortY = player.posY;
                lastSortZ = player.posZ;
                for(Iterator<ChunkCoordIntPair> it = loadedRegionsMap.keySet().iterator(); it.hasNext();) {
                    ChunkCoordIntPair k = it.next();
                    LODRegion v = loadedRegionsMap.get(k);
                    
                    if(v.distanceTaxicab(player) > renderRange * 16 + 16 * 16) {
                        System.out.println("unloading " + v);
                        v.destroy(getSaveDir());
                        it.remove();
                    } else {
                        v.tick(player);
                    }
                }
            }
        }
    }
    
    public float getFarPlaneDistanceMultiplier() {
        return renderRange / 12 * (float)LODMod.farPlaneDistanceMultiplier;
    }
    
    public void afterSetupFog(int mode, float alpha, float farPlaneDistance) {
        EntityLivingBase entity = Minecraft.getMinecraft().renderViewEntity;
        if(LODMod.fogEventWasPosted && !Minecraft.getMinecraft().theWorld.provider.doesXZShowFog((int)entity.posX, (int)entity.posZ)) {
            GL11.glFogf(GL11.GL_FOG_START, mode < 0 ? 0 : farPlaneDistance * (float)LODMod.fogStart);
            GL11.glFogf(GL11.GL_FOG_END, mode < 0 ? farPlaneDistance/4 : farPlaneDistance * (float)LODMod.fogEnd);
        }
    }
    
    private void handleKeyboard() {
        if(Keyboard.isKeyDown(Keyboard.KEY_F) && !wasDown[Keyboard.KEY_F]) {
            renderLOD = !renderLOD;
        }
        if(Keyboard.isKeyDown(Keyboard.KEY_V) && !wasDown[Keyboard.KEY_V]) {
            renderWorld = !renderWorld;
        }
        if(Keyboard.isKeyDown(Keyboard.KEY_R) && !wasDown[Keyboard.KEY_R]) {
            loadShader();
        }
        if(Keyboard.isKeyDown(Keyboard.KEY_G) && !wasDown[Keyboard.KEY_G]) {
            //LODChunk chunk = getLODChunk(9, -18);
            //setMeshVisible(chunk.chunkMeshes[7], false, true);
            //freezeMeshes = false;
            //chunk.chunkMeshes[7].quadCount = 256;
            //setMeshVisible(chunk.chunkMeshes[7], true, true);
        }
        
        for(int i = 0; i < 256; i++) {
            wasDown[i] = Keyboard.isKeyDown(i);
        }
    }
    
    private void runGC() {
        nextMeshOffset = 0;
        nextTri = 0;
        
        glBindVertexArray(VAO);
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
        int[] deletedNum = new int[2];
        int deletedRAM = 0;
        
        long t0 = System.nanoTime();
        
        for(int i = 0; i < 2; i++) {
            piFirst[i].limit(0);
            piCount[i].limit(0);
            
            for(Iterator<Mesh> it = sentMeshes[i].iterator(); it.hasNext(); ) {
                Mesh mesh = it.next();
                if(mesh.gpuStatus == GPUStatus.SENT) {
                    if(mesh.offset != nextMeshOffset) {
                        glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
                    }
                    mesh.iFirst = nextTri;
                    mesh.offset = nextMeshOffset;
                    
                    nextMeshOffset += mesh.bufferSize();
                    nextTri += mesh.quadCount * 6;
                    
                    piFirst[i].limit(piFirst[i].limit() + 1);
                    piFirst[i].put(mesh.iFirst);
                    piCount[i].limit(piCount[i].limit() + 1);
                    piCount[i].put(mesh.iCount);
                } else if(mesh.gpuStatus == GPUStatus.PENDING_DELETE) {
                    mesh.iFirst = mesh.offset = -1;
                    mesh.visible = false;
                    mesh.gpuStatus = GPUStatus.UNSENT;
                    mesh.destroyBuffer();
                    
                    it.remove();
                    deletedNum[i]++;
                    deletedRAM += mesh.bufferSize();
                }
            }
        }
        
        long t1 = System.nanoTime();
        
        System.out.println("Deleted " + deletedNum[0] + "+" + deletedNum[1] + " meshes in " + ((t1 - t0) / 1_000_000.0) + " ms, freeing up " + (deletedRAM / 1024 / 1024) + "MB of VRAM");
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
    IntBuffer viewportBuf = BufferUtils.createIntBuffer(16);
    FloatBuffer projInvBuf = BufferUtils.createFloatBuffer(16);
    FloatBuffer fogColorBuf = BufferUtils.createFloatBuffer(16);
    FloatBuffer fogStartEnd = BufferUtils.createFloatBuffer(2);
    Matrix4f projMatrix = new Matrix4f();
    
    private void render(double alpha) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        
        glUseProgram(shaderProgram);
        
        int u_modelView = glGetUniformLocation(shaderProgram, "modelView");
        int u_proj = glGetUniformLocation(shaderProgram, "proj");
        int u_playerPos = glGetUniformLocation(shaderProgram, "playerPos");
        int u_light = glGetUniformLocation(shaderProgram, "lightTex");
        int u_viewport = glGetUniformLocation(shaderProgram, "viewport");
        int u_projInv = glGetUniformLocation(shaderProgram, "projInv");
        int u_fogColor = glGetUniformLocation(shaderProgram, "fogColor");
        int u_fogStartEnd = glGetUniformLocation(shaderProgram, "fogStartEnd");
        
        if(false && (u_modelView == -1 || u_proj == -1 || u_playerPos == -1 || u_light == -1 || u_viewport == -1 || u_projInv == -1 || u_fogColor == -1 || u_fogStartEnd == -1)) {
            System.out.println("failed to get the uniform");
        } else {
            glGetFloat(GL_MODELVIEW_MATRIX, modelView);
            
            glGetFloat(GL_PROJECTION_MATRIX, projBuf);
            
            glGetInteger(GL_VIEWPORT, viewportBuf);
            
            projMatrix.load(projBuf);
            projBuf.flip();
            projMatrix.invert();
            projMatrix.store(projInvBuf);
            projInvBuf.flip();
            
            fogColorBuf.limit(16);
            glGetFloat(GL_FOG_COLOR, fogColorBuf);
            fogColorBuf.limit(4);
            
            fogStartEnd.put(glGetFloat(GL_FOG_START));
            fogStartEnd.put(glGetFloat(GL_FOG_END));
            fogStartEnd.flip();
            
            glUniformMatrix4(u_modelView, false, modelView);
            glUniformMatrix4(u_proj, false, projBuf);
            glUniformMatrix4(u_projInv, false, projInvBuf);
            glUniform4f(u_viewport, viewportBuf.get(0),viewportBuf.get(1),viewportBuf.get(2),viewportBuf.get(3));
            glUniform4(u_fogColor, fogColorBuf);
            glUniform2(u_fogStartEnd, fogStartEnd);
            
            float originX = 0;
            float originY = 0;
            float originZ = 0;
            
            Entity rve = Minecraft.getMinecraft().renderViewEntity;
            double interpX = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * alpha;
            double interpY = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * alpha + rve.getEyeHeight();
            double interpZ = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * alpha;
            
            glUniform3f(u_playerPos, (float)interpX - originX, (float)interpY - originY, (float)interpZ - originZ);
            
            glUniform1i(u_light, 1);
            
            modelView.position(0);
            projBuf.position(0);
            viewportBuf.position(0);
            projInvBuf.position(0);
            fogColorBuf.position(0);
            fogStartEnd.position(0);
        }
        
        glBindVertexArray(VAO);
        GL11.glDisable(GL11.GL_BLEND);
        glMultiDrawArrays(GL_TRIANGLES, piFirst[0], piCount[0]);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        glMultiDrawArrays(GL_TRIANGLES, piFirst[1], piCount[1]);
        
        glBindVertexArray(0);
        glUseProgram(0);
        
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        
        
    }
    
    public boolean init() {
        Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
        
        loadShader();
        
        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        
        VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        
        glBufferData(GL_ARRAY_BUFFER, BUFFER_SIZE, GL_STATIC_DRAW);
        
        int stride = 7 * 4;
        
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);
        glVertexAttribPointer(2, 2, GL_SHORT, false, stride, 5 * 4);
        glVertexAttribPointer(3, 4, GL_UNSIGNED_BYTE, false, stride, 6 * 4);
        
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);
        
        for(int i = 0; i < 2; i++) {
            piFirst[i] = BufferUtils.createIntBuffer(MAX_MESHES);
            piFirst[i].flip();
            piCount[i] = BufferUtils.createIntBuffer(MAX_MESHES);
            piCount[i].flip();
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        
        return true;
    }
    
       private void loadShader() {
            int vertexShader;
            vertexShader = glCreateShader(GL_VERTEX_SHADER);
            
            glShaderSource(vertexShader, Util.readFile("shaders/chunk.vert"));
            glCompileShader(vertexShader);
            
            if(glGetShaderi(vertexShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling vertex shader: " + glGetShaderInfoLog(vertexShader, 256));
            }
            
            int fragmentShader;
            fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            
            glShaderSource(fragmentShader, Util.readFile("shaders/chunk.frag"));
            glCompileShader(fragmentShader);
            
            if(glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling fragment shader: " + glGetShaderInfoLog(fragmentShader, 256));
            }
            
            shaderProgram = glCreateProgram();
            glAttachShader(shaderProgram, vertexShader);
            glAttachShader(shaderProgram, fragmentShader);
            glLinkProgram(shaderProgram);
            
            if(glGetProgrami(shaderProgram, GL_LINK_STATUS) == 0) {
                System.out.println("Error linking shader: " + glGetShaderInfoLog(shaderProgram, 256));
            }
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
    
    public void destroy() {
        onSave();
        
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(VAO);
        glDeleteBuffers(VBO);
        
        SimpleChunkMesh.instances = 0;
        SimpleChunkMesh.usedRAM = 0;
        ChunkMesh.instances = 0;
        ChunkMesh.usedRAM = 0;
    }
    
    public void onWorldRendererChanged(WorldRenderer wr, boolean visible) {
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        LODChunk lodChunk = getLODChunk(x, z);
        
        lodChunk.hidden[y] = LODMod.hideUnderVanillaChunks ? visible : !visible;
        lodChunkChanged(lodChunk);
    }
    
    public void onWorldRendererPost(WorldRenderer wr) {
        if(LODMod.disableChunkMeshes) return;
        
        if(Minecraft.getMinecraft().theWorld.getChunkFromChunkCoords(Math.floorDiv(wr.posX, 16), Math.floorDiv(wr.posZ, 16)).isChunkLoaded) {
            LODChunk lodChunk = getLODChunk(Math.floorDiv(wr.posX, 16), Math.floorDiv(wr.posZ, 16));
            lodChunk.putChunkMeshes(Math.floorDiv(wr.posY, 16), ((IWorldRenderer)wr).getChunkMeshes());
        }
    }
    
    private double getLastSortDistanceSq(Entity player) {
        return Math.pow(lastSortX - player.posX, 2) + Math.pow(lastSortZ - player.posZ, 2);
    }
    
    private synchronized void addToServerChunkLoadQueue(List<ChunkCoordIntPair> coords) {
        serverChunkLoadQueue.addAll(coords);
    }
    
    private LODChunk receiveFarChunk(Chunk chunk) {
        LODRegion region = getRegionContaining(chunk.xPosition, chunk.zPosition);
        return region.putChunk(chunk);
    }
    
    private LODChunk getLODChunk(int chunkX, int chunkZ) {
        return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
    }
    
    public void onStopServer() {
        
    }
    
    public synchronized void serverTick() {
        int chunkLoadsRemaining = LODMod.chunkLoadsPerTick;
        while(!serverChunkLoadQueue.isEmpty() && chunkLoadsRemaining-- > 0) {
            ChunkCoordIntPair coords = serverChunkLoadQueue.remove(0);
            ChunkProviderServer chunkProviderServer = Minecraft.getMinecraft().getIntegratedServer().worldServerForDimension(world.provider.dimensionId).theChunkProviderServer;
            Chunk chunk = chunkProviderServer.currentChunkProvider.provideChunk(coords.chunkXPos, coords.chunkZPos);
            SimpleChunkMesh.prepareFarChunkOnServer(chunk);
            farChunks.add(chunk);
        }
    }
    
    private LODRegion getRegionContaining(int chunkX, int chunkZ) {
        ChunkCoordIntPair key = new ChunkCoordIntPair(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ, 32));
        LODRegion region = loadedRegionsMap.get(key);
        if(region == null) {
            region = LODRegion.load(getSaveDir(), Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ , 32));
            loadedRegionsMap.put(key, region);
        }
        return region;
    }
    
    private void sendChunkToGPU(LODChunk lodChunk) {
        Entity player = Minecraft.getMinecraft().renderViewEntity;
        
        lodChunk.tick(player);
        setVisible(lodChunk, true, true);
    }
    
    public void setVisible(LODChunk chunk, boolean visible) {
        setVisible(chunk, visible, false);
    }
    
    public void setVisible(LODChunk lodChunk, boolean visible, boolean forceCheck) {
        if(!forceCheck && visible == lodChunk.visible) return;
        
        lodChunk.visible = visible;
        lodChunkChanged(lodChunk);
    }
    
    public void lodChunkChanged(LODChunk lodChunk) {
        int newLOD = (!lodChunk.hasChunkMeshes() && lodChunk.lod == 2) ? (LODMod.disableSimpleMeshes ? 0 : 1) : lodChunk.lod;
        for(SimpleChunkMesh sm : lodChunk.simpleMeshes) {
            if(sm != null) {
                if(lodChunk.isFullyVisible() && newLOD == 1) {
                    if(!sm.visible) {
                        setMeshVisible(sm, true);
                    }
                } else {
                    if(sm.visible) {
                        setMeshVisible(sm, false);
                    }
                }
            }
        }
        for(int y = 0; y < 16; y++) {
            for(int pass = 0; pass < 2; pass++) {
                ChunkMesh cm = lodChunk.chunkMeshes[y * 2 + pass];
                if(cm != null) {
                    if(lodChunk.isSubchunkVisible(y) && newLOD == 2) {
                        if(!cm.visible) {
                            setMeshVisible(cm, true);
                        }
                    } else {
                        if(cm.visible) {
                            setMeshVisible(cm, false);
                        }
                    }
                }
            }
        }
    }
    
    private int nextTri;
    private int nextMeshOffset;
    private int nextMesh;
    
    protected void setMeshVisible(Mesh mesh, boolean visible) {
        setMeshVisible(mesh, visible, false);
    }
    
    protected void setMeshVisible(Mesh mesh, boolean visible, boolean force) {
        if((!force && freezeMeshes) || mesh == null) return;
        
        if(mesh.visible != visible) {
            mesh.visible = visible;
            
            if(mesh.gpuStatus == GPUStatus.UNSENT) {
                sendMeshToGPU(mesh);
            }
        }
    }
    
    public void removeMesh(Mesh mesh) {
        deleteMeshFromGPU(mesh);
    }
    
    private void sendMeshToGPU(Mesh mesh) {
        if(mesh == null) {
            return;
        }
        if(mesh.gpuStatus == GPUStatus.UNSENT) {
            mesh.prepareBuffer();
            
            glBindVertexArray(VAO);
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            
            glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
            mesh.iFirst = nextTri;
            mesh.iCount = mesh.quadCount * 6;
            mesh.offset = nextMeshOffset;
            
            nextTri += mesh.quadCount * 6;
            nextMeshOffset += mesh.buffer.limit();
            sentMeshes[mesh.pass].add(mesh);
            
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
        
        mesh.gpuStatus = GPUStatus.SENT;
    }
    
    private void deleteMeshFromGPU(Mesh mesh) {
        if(mesh == null || mesh.gpuStatus == GPUStatus.UNSENT) {
            return;
        }
        mesh.gpuStatus = GPUStatus.PENDING_DELETE;
    }   
    
    public Chunk getChunkFromChunkCoords(int x, int z) {
        for(Chunk chunk : myChunks) {
            if(chunk.xPosition == x && chunk.zPosition == z) {
                return chunk;
            }
        }
        return null;
    }
    
    public boolean shouldSideBeRendered(Block block, IBlockAccess ba, int x, int y, int z, int w) {
        EnumFacing facing = EnumFacing.values()[w];
        if(block.getMaterial() == Material.water && facing != EnumFacing.UP && facing != EnumFacing.DOWN && !Minecraft.getMinecraft().theWorld.getChunkFromBlockCoords(x, z).isChunkLoaded) {
            return false;
        } else {
            return block.shouldSideBeRendered(ba, x, y, z, w);
        }
    }
    
    public List<String> getDebugText() {
        return Arrays.asList(
                "VRAM: " + (nextMeshOffset / 1024 / 1024) + "MB / " + (BUFFER_SIZE / 1024 / 1024) + "MB",
                "Simple meshes: " + SimpleChunkMesh.instances + " (" + SimpleChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Full meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Total RAM used: " + ((SimpleChunkMesh.usedRAM + ChunkMesh.usedRAM) / 1024 / 1024) + " MB",
                "Rendered: " + renderedMeshes
        );
    }
    
    public void onSave() {
        System.out.println("Saving LOD regions...");
        long t0 = System.currentTimeMillis();
        loadedRegionsMap.forEach((k, v) -> v.save(getSaveDir()));
        System.out.println("Finished saving LOD regions in " + ((System.currentTimeMillis() - t0) / 1000.0) + "s");
    }
    
    public void onChunkLoad(ChunkEvent.Load event) {
        farChunks.add(event.getChunk());
    }
    
    private Path getSaveDir(){
        return Minecraft.getMinecraft().mcDataDir.toPath().resolve("lodmod").resolve(Minecraft.getMinecraft().getIntegratedServer().getFolderName());
    }
    
    private boolean shouldRenderInWorld(World world) {
        return world != null && !world.provider.isHellWorld;
    }
    
    public static class LODChunkComparator implements Comparator<LODChunk> {
        Entity player;
        
        public LODChunkComparator(Entity player) {
            this.player = player;
        }
        
        @Override
        public int compare(LODChunk p1, LODChunk p2) {
            int distSq1 = distSq(p1);
            int distSq2 = distSq(p2);
            return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
        }
        
        int distSq(LODChunk p) {
            return (int)(
                    Math.pow(((p.x * 16) - player.chunkCoordX), 2) +
                    Math.pow(((p.z * 16) - player.chunkCoordZ), 2)
                    );
        }
    }
    
    public static class ChunkCoordDistanceComparator implements Comparator<ChunkCoordIntPair> {
        double x, y, z;
        
        public ChunkCoordDistanceComparator(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public int compare(ChunkCoordIntPair p1, ChunkCoordIntPair p2) {
            int distSq1 = distSq(p1);
            int distSq2 = distSq(p2);
            return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
        }
        
        int distSq(ChunkCoordIntPair p) {
            return (int)(
                    Math.pow(((p.chunkXPos * 16) - x), 2) +
                    Math.pow(((p.chunkZPos * 16) - z), 2)
                    );
        }
    }
    
    public static class MeshDistanceComparator implements Comparator<Mesh> {
        double x, y, z;
        
        MeshDistanceComparator(double x, double y, double z){
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public int compare(Mesh a, Mesh b) {
            if(a.pass < b.pass) {
                return -1;
            } else if(a.pass > b.pass) {
                return 1;
            } else {
                double distSqA = a.distSq(x, y, z);
                double distSqB = b.distSq(x, y, z);
                if(distSqA > distSqB) {
                    return 1;
                } else if(distSqA < distSqB) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
        
    }
}