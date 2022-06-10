package makamys.neodymium.renderer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FOG_COLOR;
import static org.lwjgl.opengl.GL11.GL_FOG_END;
import static org.lwjgl.opengl.GL11.GL_FOG_START;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL14.glMultiDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import makamys.neodymium.Config;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.util.GuiHelper;
import makamys.neodymium.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.ChunkEvent;

/** The main renderer class. */
public class NeoRenderer {
    
    public boolean hasInited = false;
    public boolean destroyPending;
    
    private boolean[] wasDown = new boolean[256];
    private int renderQuads = 0;
    
    public boolean renderWorld;
    public boolean rendererActive;
    private boolean showMemoryDebugger;
    
    private static int MAX_MESHES = 100000;
    
    private int VAO, shaderProgram;
    private IntBuffer[] piFirst = new IntBuffer[2];
    private IntBuffer[] piCount = new IntBuffer[2];
    private List<Mesh>[] sentMeshes = (List<Mesh>[])new ArrayList[] {new ArrayList<Mesh>(), new ArrayList<Mesh>()};
    GPUMemoryManager mem;
    
    List<Chunk> myChunks = new ArrayList<Chunk>();
    List<NeoChunk> pendingLODChunks = new ArrayList<>();
    
    private boolean hasServerInited = false;
    private Map<ChunkCoordIntPair, NeoRegion> loadedRegionsMap = new HashMap<>();
    
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
    
    public NeoRenderer(World world){
        this.world = world;
        if(shouldRenderInWorld(world)) {
            hasInited = init();
        }
        
        renderWorld = true;
        rendererActive = true;
    }
    
    public void preRenderSortedRenderers(int renderPass, double alpha, WorldRenderer[] sortedWorldRenderers) {
        if(renderPass != 0) return;
        
        Neodymium.fogEventWasPosted = false;
        
        renderedMeshes = 0;
        
        Minecraft.getMinecraft().entityRenderer.enableLightmap((double)alpha);
        
        if(hasInited) {
            mainLoop();
            if(Minecraft.getMinecraft().currentScreen == null) {
                handleKeyboard();
            }
            if(frameCount % 2 == 0) {
                mem.runGC(false);
            }
            lastGCTime = System.currentTimeMillis();
            if(lastSaveTime == -1 || (System.currentTimeMillis() - lastSaveTime) > saveInterval && Config.saveMeshes) {
                onSave();
                lastSaveTime = System.currentTimeMillis();
            }
            
            if(rendererActive && renderWorld) {
                if(frameCount % Config.sortFrequency == 0) {
                    sort();
                }
                
                updateMeshes();
                initIndexBuffers();
                render(alpha);
            }
        }
        
        frameCount++;
        
        Minecraft.getMinecraft().entityRenderer.disableLightmap((double)alpha);
    }
    
    public void onRenderTickEnd() {
        if(destroyPending) {
            Neodymium.renderer = null;
            return;
        }
        if(showMemoryDebugger && mem != null) {
            GuiHelper.begin();
            mem.drawInfo();
            GuiHelper.end();
        }
    }
    
    private void sort() {
        Entity player = Minecraft.getMinecraft().renderViewEntity;
        for(List<Mesh> list : sentMeshes) {
            list.sort(new MeshDistanceComparator(Math.floor(player.posX / 16.0), Math.floor(player.posY / 16.0), Math.floor(player.posZ / 16.0)));
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
            piFirst[i].limit(sentMeshes[i].size());
            piCount[i].limit(sentMeshes[i].size());
            for(Mesh mesh : sentMeshes[i]) {
                if(mesh.visible && (Config.maxMeshesPerFrame == -1 || renderedMeshes < Config.maxMeshesPerFrame)) {
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
            NeoChunk lodChunk = receiveFarChunk(farChunks.remove());
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
                    NeoRegion v = loadedRegionsMap.get(k);
                    
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
        return (float)Config.farPlaneDistanceMultiplier;
    }
    
    public void afterSetupFog(int mode, float alpha, float farPlaneDistance) {
        EntityLivingBase entity = Minecraft.getMinecraft().renderViewEntity;
        if(Neodymium.fogEventWasPosted && !Minecraft.getMinecraft().theWorld.provider.doesXZShowFog((int)entity.posX, (int)entity.posZ)) {
            GL11.glFogf(GL11.GL_FOG_START, mode < 0 ? 0 : farPlaneDistance * (float)Config.fogStart);
            GL11.glFogf(GL11.GL_FOG_END, mode < 0 ? farPlaneDistance/4 : farPlaneDistance * (float)Config.fogEnd);
        }
    }
    
    private void handleKeyboard() {
        if(Config.debugPrefix == 0 || (Config.debugPrefix != -1 && Keyboard.isKeyDown(Config.debugPrefix))) {
            if(Keyboard.isKeyDown(Keyboard.KEY_F) && !wasDown[Keyboard.KEY_F]) {
                rendererActive = !rendererActive;
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_V) && !wasDown[Keyboard.KEY_V]) {
                renderWorld = !renderWorld;
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_R) && !wasDown[Keyboard.KEY_R]) {
                reloadShader();
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_M) && !wasDown[Keyboard.KEY_M]) {
                showMemoryDebugger = !showMemoryDebugger;
                //LODChunk chunk = getLODChunk(9, -18);
                //setMeshVisible(chunk.chunkMeshes[7], false, true);
                //freezeMeshes = false;
                //chunk.chunkMeshes[7].quadCount = 256;
                //setMeshVisible(chunk.chunkMeshes[7], true, true);
            }
        }
        for(int i = 0; i < 256; i++) {
            wasDown[i] = Keyboard.isKeyDown(i);
        }
    }
    
    FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
    IntBuffer viewportBuf = BufferUtils.createIntBuffer(16);
    FloatBuffer projInvBuf = BufferUtils.createFloatBuffer(16);
    FloatBuffer fogColorBuf = BufferUtils.createFloatBuffer(16);
    FloatBuffer fogStartEnd = BufferUtils.createFloatBuffer(2);
    Matrix4f projMatrix = new Matrix4f();
    
    private void render(double alpha) {
        if(shaderProgram == 0) return;
        
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
        
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        glMultiDrawArrays(GL_TRIANGLES, piFirst[0], piCount[0]);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        glMultiDrawArrays(GL_TRIANGLES, piFirst[1], piCount[1]);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
        
        glBindVertexArray(0);
        glUseProgram(0);
        
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        
        
    }
    
    public boolean init() {
        Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
        
        reloadShader();
        
        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        
        mem = new GPUMemoryManager();
        
        glBindBuffer(GL_ARRAY_BUFFER, mem.VBO);
        
        int stride = MeshQuad.getStride();
        
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4);
        glVertexAttribPointer(2, 2, GL_SHORT, false, stride, 5 * 4);
        glVertexAttribPointer(3, 4, GL_UNSIGNED_BYTE, false, stride, 6 * 4);
        glVertexAttribPointer(4, 4, GL_UNSIGNED_BYTE, false, stride, 7 * 4);
        // we only need 2 bytes for attribute 4, but GL freaks out if the stride isn't a multiple of 4, so we have 2 unused bytes
        
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);
        glEnableVertexAttribArray(4);
        
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
    
       public void reloadShader() {
            boolean errors = false;
           
            int vertexShader;
            vertexShader = glCreateShader(GL_VERTEX_SHADER);
            
            glShaderSource(vertexShader, Util.readFile("shaders/chunk.vert"));
            glCompileShader(vertexShader);
            
            if(glGetShaderi(vertexShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling vertex shader: " + glGetShaderInfoLog(vertexShader, 256));
                errors = true;
            }
            
            int fragmentShader;
            fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            
            glShaderSource(fragmentShader, Util.readFile(Config.renderFog ? "shaders/chunk_fog.frag" : "shaders/chunk.frag"));
            glCompileShader(fragmentShader);
            
            if(glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling fragment shader: " + glGetShaderInfoLog(fragmentShader, 256));
                errors = true;
            }
            
            int newShaderProgram = glCreateProgram();
            glAttachShader(newShaderProgram, vertexShader);
            glAttachShader(newShaderProgram, fragmentShader);
            glLinkProgram(newShaderProgram);
            
            if(glGetProgrami(newShaderProgram, GL_LINK_STATUS) == 0) {
                System.out.println("Error linking shader: " + glGetShaderInfoLog(newShaderProgram, 256));
                errors = true;
            }
            
            if(!errors) {
                shaderProgram = newShaderProgram;
            }
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
    
    public void destroy() {
        onSave();
        
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(VAO);
        mem.destroy();
        
        SimpleChunkMesh.instances = 0;
        SimpleChunkMesh.usedRAM = 0;
        ChunkMesh.instances = 0;
        ChunkMesh.usedRAM = 0;
    }
    
    public void onWorldRendererChanged(WorldRenderer wr, WorldRendererChange change) {
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        NeoChunk lodChunk = getLODChunk(x, z);
        
        lodChunk.isSectionVisible[y] = change == WorldRendererChange.VISIBLE;
        if(change == WorldRendererChange.DELETED) {
            removeMesh(lodChunk.chunkMeshes[y]);
        }
        lodChunkChanged(lodChunk);
    }
    
    public void onWorldRendererPost(WorldRenderer wr) {
        if(Config.disableChunkMeshes) return;
        
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        
        if(Minecraft.getMinecraft().theWorld.getChunkFromChunkCoords(x, z).isChunkLoaded) {
            NeoChunk lodChunk = getLODChunk(x, z);
            lodChunk.isSectionVisible[y] = ((IWorldRenderer)wr).isDrawn();
            lodChunk.putChunkMeshes(y, ((IWorldRenderer)wr).getChunkMeshes());
        }
    }
    
    private double getLastSortDistanceSq(Entity player) {
        return Math.pow(lastSortX - player.posX, 2) + Math.pow(lastSortZ - player.posZ, 2);
    }
    
    private synchronized void addToServerChunkLoadQueue(List<ChunkCoordIntPair> coords) {
        serverChunkLoadQueue.addAll(coords);
    }
    
    private NeoChunk receiveFarChunk(Chunk chunk) {
        NeoRegion region = getRegionContaining(chunk.xPosition, chunk.zPosition);
        return region.putChunk(chunk);
    }
    
    private NeoChunk getLODChunk(int chunkX, int chunkZ) {
        return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
    }
    
    public void onStopServer() {
        
    }
    
    public synchronized void serverTick() {
        int chunkLoadsRemaining = Config.chunkLoadsPerTick;
        while(!serverChunkLoadQueue.isEmpty() && chunkLoadsRemaining-- > 0) {
            ChunkCoordIntPair coords = serverChunkLoadQueue.remove(0);
            ChunkProviderServer chunkProviderServer = Minecraft.getMinecraft().getIntegratedServer().worldServerForDimension(world.provider.dimensionId).theChunkProviderServer;
            Chunk chunk = chunkProviderServer.currentChunkProvider.provideChunk(coords.chunkXPos, coords.chunkZPos);
            SimpleChunkMesh.prepareFarChunkOnServer(chunk);
            farChunks.add(chunk);
        }
    }
    
    private NeoRegion getRegionContaining(int chunkX, int chunkZ) {
        ChunkCoordIntPair key = new ChunkCoordIntPair(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ, 32));
        NeoRegion region = loadedRegionsMap.get(key);
        if(region == null) {
            region = NeoRegion.load(getSaveDir(), Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ , 32));
            loadedRegionsMap.put(key, region);
        }
        return region;
    }
    
    private void sendChunkToGPU(NeoChunk lodChunk) {
        Entity player = Minecraft.getMinecraft().renderViewEntity;
        
        lodChunk.tick(player);
        setVisible(lodChunk, true, true);
    }
    
    public void setVisible(NeoChunk chunk, boolean visible) {
        setVisible(chunk, visible, false);
    }
    
    public void setVisible(NeoChunk lodChunk, boolean visible, boolean forceCheck) {
        if(!forceCheck && visible == lodChunk.visible) return;
        
        lodChunk.visible = visible;
        lodChunkChanged(lodChunk);
    }
    
    public void lodChunkChanged(NeoChunk lodChunk) {
        int newLOD = (!lodChunk.hasChunkMeshes() && lodChunk.lod == 2) ? (Config.disableSimpleMeshes ? 0 : 1) : lodChunk.lod;
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
                    if(lodChunk.isSectionVisible[y] && newLOD == 2) {
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
    
    protected void setMeshVisible(Mesh mesh, boolean visible) {
        setMeshVisible(mesh, visible, false);
    }
    
    protected void setMeshVisible(Mesh mesh, boolean visible, boolean force) {
        if((!force && freezeMeshes) || mesh == null) return;
        
        if(mesh.visible != visible) {
            mesh.visible = visible;
            
            if(mesh.gpuStatus == GPUStatus.UNSENT) {
                mem.sendMeshToGPU(mesh);
                sentMeshes[mesh.pass].add(mesh);
            }
        }
    }
    
    public void removeMesh(Mesh mesh) {
        if(mesh == null) return;
        
        mem.deleteMeshFromGPU(mesh);
        sentMeshes[mesh.pass].remove(mesh);
        setMeshVisible(mesh, false);
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
        List<String> text = new ArrayList<>();
        text.addAll(mem.getDebugText());
        text.addAll(Arrays.asList(
                "Simple meshes: " + SimpleChunkMesh.instances + " (" + SimpleChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Full meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Total RAM used: " + ((SimpleChunkMesh.usedRAM + ChunkMesh.usedRAM) / 1024 / 1024) + " MB",
                "Rendered: " + renderedMeshes
        ));
        return text;
    }
    
    public void onSave() {
        System.out.println("Saving LOD regions...");
        long t0 = System.currentTimeMillis();
        //loadedRegionsMap.forEach((k, v) -> v.save(getSaveDir()));
        System.out.println("Finished saving LOD regions in " + ((System.currentTimeMillis() - t0) / 1000.0) + "s");
    }
    
    public void onChunkLoad(ChunkEvent.Load event) {
        farChunks.add(event.getChunk());
    }
    
    private Path getSaveDir(){
        return Minecraft.getMinecraft().mcDataDir.toPath().resolve("neodymium").resolve(Minecraft.getMinecraft().getIntegratedServer().getFolderName());
    }
    
    private boolean shouldRenderInWorld(World world) {
        return world != null && !world.provider.isHellWorld;
    }
    
    public static class LODChunkComparator implements Comparator<NeoChunk> {
        Entity player;
        
        public LODChunkComparator(Entity player) {
            this.player = player;
        }
        
        @Override
        public int compare(NeoChunk p1, NeoChunk p2) {
            int distSq1 = distSq(p1);
            int distSq2 = distSq(p2);
            return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
        }
        
        int distSq(NeoChunk p) {
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
    
    public static enum WorldRendererChange {
        VISIBLE, INVISIBLE, DELETED
    }
}