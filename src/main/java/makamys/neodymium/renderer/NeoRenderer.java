package makamys.neodymium.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
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
import makamys.neodymium.util.Preprocessor;
import makamys.neodymium.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

/** The main renderer class. */
public class NeoRenderer {
    
    private static final MeshDistanceComparator DISTANCE_COMPARATOR = new MeshDistanceComparator();
    
    public boolean hasInited = false;
    public boolean destroyPending;
    public boolean reloadPending;
    
    private static boolean[] wasDown = new boolean[256];
    
    public boolean renderWorld;
    public boolean rendererActive;
    private boolean showMemoryDebugger;
    
    private static int MAX_MESHES = 100000;
    
    private int VAO;
    private int[] shaderPrograms = {0, 0};
    private IntBuffer[] piFirst = new IntBuffer[2];
    private IntBuffer[] piCount = new IntBuffer[2];
    private List<Mesh>[] sentMeshes = (List<Mesh>[])new ArrayList[] {new ArrayList<Mesh>(), new ArrayList<Mesh>()};
    GPUMemoryManager mem;
    
    List<Chunk> myChunks = new ArrayList<Chunk>();
    List<NeoChunk> pendingLODChunks = new ArrayList<>();
    
    private Map<ChunkCoordIntPair, NeoRegion> loadedRegionsMap = new HashMap<>();
    
    public World world;
    
    private double interpX;
    private double interpY;
    private double interpZ;
    int interpXDiv;
    int interpYDiv;
    int interpZDiv;
    
    private int renderedMeshes, renderedQuads;
    private int frameCount;
    
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
        
        renderedMeshes = 0;
        renderedQuads = 0;
        
        Minecraft.getMinecraft().entityRenderer.enableLightmap((double)alpha);
        
        if(hasInited) {
            mainLoop();
            if(Minecraft.getMinecraft().currentScreen == null) {
                handleKeyboard();
            }
            if(mem.getCoherenceRate() < 0.95f || frameCount % 4 == 0) {
                mem.runGC(false);
            }
            
            if(rendererActive && renderWorld) {
                Entity rve = Minecraft.getMinecraft().renderViewEntity;
                
                interpX = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * alpha;
                interpY = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * alpha + rve.getEyeHeight();
                interpZ = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * alpha;
                
                interpXDiv = Math.floorDiv((int)Math.floor(interpX), 16);
                interpYDiv = Math.floorDiv((int)Math.floor(interpY), 16);
                interpZDiv = Math.floorDiv((int)Math.floor(interpZ), 16);
                
                sort(frameCount % 100 == 0, frameCount % Config.sortFrequency == 0);
                
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
            destroy();
            Neodymium.renderer = null;
            return;
        } else if(reloadPending) {
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
        if(showMemoryDebugger && mem != null) {
            GuiHelper.begin();
            mem.drawInfo();
            GuiHelper.end();
        }
    }
    
    private void sort(boolean pass0, boolean pass1) {
        if(pass0) {
            sentMeshes[0].sort(DISTANCE_COMPARATOR.setOrigin(interpX, interpY, interpZ).setInverted(false));
        }
        if(pass1) {
            sentMeshes[1].sort(DISTANCE_COMPARATOR.setOrigin(interpX, interpY, interpZ).setInverted(true));
        }
    }
    
    private void updateMeshes() {
        for(List<Mesh> list : sentMeshes) {
            for(Mesh mesh : list) {
                mesh.update();
            }
        }
    }
    
    private static int[] renderedMeshesReturn = new int[1];
    private static int[] renderedQuadsReturn = new int[1];
    
    private void initIndexBuffers() {
        for(int i = 0; i < 2; i++) {
            piFirst[i].limit(MAX_MESHES);
            piCount[i].limit(MAX_MESHES);
            for(Mesh mesh : sentMeshes[i]) {
                if(mesh.visible && (Config.maxMeshesPerFrame == -1 || renderedMeshes < Config.maxMeshesPerFrame)) {
                    mesh.writeToIndexBuffer(piFirst[i], piCount[i], renderedMeshesReturn, renderedQuadsReturn, interpXDiv, interpYDiv, interpZDiv);
                    renderedMeshes += renderedMeshesReturn[0];
                    renderedQuads += renderedQuadsReturn[0];
                }
            }
            piFirst[i].flip();
            piCount[i].flip();
        }
    }
    
    private void mainLoop() {
        if(Minecraft.getMinecraft().playerController.netClientHandler.doneLoadingTerrain) {
            for(Iterator<Entry<ChunkCoordIntPair, NeoRegion>> it = loadedRegionsMap.entrySet().iterator(); it.hasNext();) {
                Entry<ChunkCoordIntPair, NeoRegion> kv = it.next();
                NeoRegion v = kv.getValue();
                
                if(v.shouldDelete()) {
                    v.destroy(getSaveDir());
                    it.remove();
                } else {
                    v.tick();
                }
            }
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
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_P) && !wasDown[Keyboard.KEY_P]) {
                Util.dumpTexture();
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_LEFT) && !wasDown[Keyboard.KEY_LEFT]) {
                reloadPending = true;
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
        if(shaderPrograms[0] == 0 || shaderPrograms[1] == 0) return;
        
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        
        glBindVertexArray(VAO);
        GL11.glDisable(GL11.GL_BLEND);
        
        glUseProgram(shaderPrograms[0]);
        updateUniforms(alpha, 0);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        glMultiDrawArrays(GL_QUADS, piFirst[0], piCount[0]);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
        
        glUseProgram(shaderPrograms[1]);
        updateUniforms(alpha, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        glMultiDrawArrays(GL_QUADS, piFirst[1], piCount[1]);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
        
        glBindVertexArray(0);
        glUseProgram(0);
        
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }
    
    private void updateUniforms(double alpha, int pass) {
        int shaderProgram = shaderPrograms[pass];
        
        int u_modelView = glGetUniformLocation(shaderProgram, "modelView");
        int u_proj = glGetUniformLocation(shaderProgram, "proj");
        int u_playerPos = glGetUniformLocation(shaderProgram, "playerPos");
        int u_light = glGetUniformLocation(shaderProgram, "lightTex");
        int u_viewport = glGetUniformLocation(shaderProgram, "viewport");
        int u_projInv = glGetUniformLocation(shaderProgram, "projInv");
        int u_fogColor = glGetUniformLocation(shaderProgram, "fogColor");
        int u_fogStartEnd = glGetUniformLocation(shaderProgram, "fogStartEnd");
        
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
        
        if(Config.renderFog) {
            fogStartEnd.put(glGetFloat(GL_FOG_START));
            fogStartEnd.put(glGetFloat(GL_FOG_END));
        } else {
            fogStartEnd.put(-1);
            fogStartEnd.put(-1);
        }
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
        
        glUniform3f(u_playerPos, (float)interpX - originX, (float)interpY - originY, (float)interpZ - originZ);
        
        glUniform1i(u_light, 1);
        
        modelView.position(0);
        projBuf.position(0);
        viewportBuf.position(0);
        projInvBuf.position(0);
        fogColorBuf.position(0);
        fogStartEnd.position(0);
    }
    
    public boolean init() {
        reloadShader();
        
        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        
        mem = new GPUMemoryManager();
        
        glBindBuffer(GL_ARRAY_BUFFER, mem.VBO);
        
        int stride = MeshQuad.getStride();
        
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 2, Config.shortUV ? GL_UNSIGNED_SHORT : GL_FLOAT, false, stride, 3 * 4);
        int uvEnd = Config.shortUV ? 4 * 4 : 5 * 4;
        glVertexAttribPointer(2, 2, GL_SHORT, false, stride, uvEnd);
        glVertexAttribPointer(3, 4, GL_UNSIGNED_BYTE, false, stride, uvEnd + 1 * 4);
        if(Config.simplifyChunkMeshes) {
            glVertexAttribPointer(4, 4, GL_UNSIGNED_BYTE, false, stride, uvEnd + 2 * 4);
        }
        
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glEnableVertexAttribArray(3);
        if(Config.simplifyChunkMeshes) {
            glEnableVertexAttribArray(4);
        }
        
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
    
       public void reloadShader(int pass) {
           Set<String> defines = new HashSet<>();
           if(Config.renderFog) {
               defines.add("RENDER_FOG");
           }
           if(Config.simplifyChunkMeshes) {
               defines.add("SIMPLIFY_MESHES");
           }
           if(Config.shortUV) {
               defines.add("SHORT_UV");
           }
           if(pass == 0) {
               defines.add("PASS_0");
           }
           
            boolean errors = false;
            
            int vertexShader;
            vertexShader = glCreateShader(GL_VERTEX_SHADER);
            
            glShaderSource(vertexShader, Preprocessor.preprocess(Util.readFile("shaders/chunk.vert"), defines));
            glCompileShader(vertexShader);
            
            if(glGetShaderi(vertexShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling vertex shader: " + glGetShaderInfoLog(vertexShader, 256));
                errors = true;
            }
            
            int fragmentShader;
            fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            
            glShaderSource(fragmentShader, Preprocessor.preprocess(Util.readFile("shaders/chunk.frag"), defines));
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
                shaderPrograms[pass] = newShaderProgram;
            }
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
       
    public void reloadShader() {
        reloadShader(0);
        reloadShader(1);
    }
    
    public void destroy() {
        glDeleteProgram(shaderPrograms[0]);
        glDeleteProgram(shaderPrograms[1]);
        glDeleteVertexArrays(VAO);
        mem.destroy();
        
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
            if(lodChunk.chunkMeshes[y] != null) {
                lodChunk.chunkMeshes[y].destroy();
                lodChunk.chunkMeshes[y] = null;
                lodChunk.region.meshes--;
            }
        }
        lodChunkChanged(lodChunk);
    }
    
    public void onWorldRendererPost(WorldRenderer wr) {
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        
        if(Minecraft.getMinecraft().theWorld.getChunkFromChunkCoords(x, z).isChunkLoaded) {
            NeoChunk lodChunk = getLODChunk(x, z);
            lodChunk.isSectionVisible[y] = ((IWorldRenderer)wr).isDrawn();
            lodChunk.putChunkMeshes(y, ((IWorldRenderer)wr).getChunkMeshes());
        }
    }
    
    private NeoChunk getLODChunk(int chunkX, int chunkZ) {
        return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
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
    
    public void setVisible(NeoChunk chunk, boolean visible) {
        setVisible(chunk, visible, false);
    }
    
    public void setVisible(NeoChunk lodChunk, boolean visible, boolean forceCheck) {
        if(!forceCheck && visible == lodChunk.visible) return;
        
        lodChunk.visible = visible;
        lodChunkChanged(lodChunk);
    }
    
    public void lodChunkChanged(NeoChunk lodChunk) {
        int newLOD = lodChunk.hasChunkMeshes() ? 2 : 0;
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
        if(mesh == null) return;
        
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
        text.addAll(Arrays.asList(
                "Neodymium " + Neodymium.VERSION
        ));
        text.addAll(mem.getDebugText());
        text.addAll(Arrays.asList(
                "Meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Rendered: " + renderedMeshes + " (" + renderedQuads / 1000 + "KQ)"
        ));
        return text;
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
        boolean inverted;
        
        public Comparator<? super Mesh> setInverted(boolean inverted) {
            this.inverted = inverted;
            return this;
        }

        public MeshDistanceComparator setOrigin(double x, double y, double z) {
            this.x = x / 16.0;
            this.y = y / 16.0;
            this.z = z / 16.0;
            return this;
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
                
                int mult = inverted ? -1 : 1;
                
                if(distSqA > distSqB) {
                    return 1 * mult;
                } else if(distSqA < distSqB) {
                    return -1 * mult;
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