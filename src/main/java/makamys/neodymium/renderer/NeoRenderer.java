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
import java.util.Set;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import makamys.neodymium.Config;
import makamys.neodymium.Neodymium;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.util.GuiHelper;
import makamys.neodymium.util.Preprocessor;
import makamys.neodymium.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

/** The main renderer class. */
public class NeoRenderer {
    
    private static final MeshDistanceComparator DISTANCE_COMPARATOR = new MeshDistanceComparator();
    
    public boolean hasInited = false;
    public boolean destroyPending;
    public boolean reloadPending;
    public int rendererSpeedup;
    
    private static boolean[] wasDown = new boolean[256];
    
    public boolean renderWorld;
    public boolean rendererActive;
    private boolean showMemoryDebugger;
    
    public boolean forceRenderFog;
    
    private static int MAX_MESHES = 100000;
    
    private int VAO;
    private int[] shaderProgramsFog = {0, 0};
    private int[] shaderProgramsNoFog = {0, 0};
    private IntBuffer[] piFirst = new IntBuffer[2];
    private IntBuffer[] piCount = new IntBuffer[2];
    private List<Mesh>[] sentMeshes = (List<Mesh>[])new ArrayList[] {new ArrayList<Mesh>(), new ArrayList<Mesh>()};
    GPUMemoryManager mem;
    
    private Map<ChunkCoordIntPair, NeoRegion> loadedRegionsMap = new HashMap<>();
    
    public World world;
    
    // Eye position in world space
    private double eyePosX;
    private double eyePosY;
    private double eyePosZ;
    
    // Eye position in world space, transformed by model-view matrix (takes third person camera offset into account)
    private double eyePosXT;
    private double eyePosYT;
    private double eyePosZT;
    
    // eyePos?T divided by 16 
    int eyePosXTDiv;
    int eyePosYTDiv;
    int eyePosZTDiv;
    
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
    
    Vector4f transformedOrigin = new Vector4f();
    
    public void preRenderSortedRenderers(int renderPass, double alpha, WorldRenderer[] sortedWorldRenderers) {
        if(hasInited) {
            if(renderPass == 0) {
                renderedMeshes = 0;
                renderedQuads = 0;
                
                mainLoop();
                if(Minecraft.getMinecraft().currentScreen == null) {
                    handleKeyboard();
                }
                if(mem.getCoherenceRate() < 0.95f || frameCount % 4 == 0) {
                    mem.runGC(false);
                }
                
                if(rendererActive && renderWorld) {
                    updateGLValues();
                    
                    transformedOrigin.set(0, 0, 0, 1);
                    Matrix4f.transform(modelViewMatrixInv, transformedOrigin, transformedOrigin);
                    
                    Entity rve = Minecraft.getMinecraft().renderViewEntity;
                    
                    eyePosX = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * alpha;
                    eyePosY = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * alpha + rve.getEyeHeight();
                    eyePosZ = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * alpha;
                    
                    eyePosXT = eyePosX + transformedOrigin.x;
                    eyePosYT = eyePosY + transformedOrigin.y;
                    eyePosZT = eyePosZ + transformedOrigin.z;
                    
                    eyePosXTDiv = Math.floorDiv((int)Math.floor(eyePosXT), 16);
                    eyePosYTDiv = Math.floorDiv((int)Math.floor(eyePosYT), 16);
                    eyePosZTDiv = Math.floorDiv((int)Math.floor(eyePosZT), 16);
                    
                    sort(frameCount % 100 == 0, frameCount % Config.sortFrequency == 0);
                    
                    updateMeshes();
                    initIndexBuffers();
                }
                
                frameCount++;
            }
            
            if(rendererActive && renderWorld) {
                Minecraft.getMinecraft().entityRenderer.enableLightmap((double)alpha);
                
                render(renderPass, alpha);
                
                Minecraft.getMinecraft().entityRenderer.disableLightmap((double)alpha);
            }
        }
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
            sentMeshes[0].sort(DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ).setInverted(false));
        }
        if(pass1) {
            sentMeshes[1].sort(DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ).setInverted(true));
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
            piFirst[i].limit(MAX_MESHES);
            piCount[i].limit(MAX_MESHES);
            for(Mesh mesh : sentMeshes[i]) {
                if(shouldRenderMesh(mesh)) {
                    int meshes = mesh.writeToIndexBuffer(piFirst[i], piCount[i], eyePosXTDiv, eyePosYTDiv, eyePosZTDiv);
                    renderedMeshes += meshes;
                    for(int j = piCount[i].position() - meshes; j < piCount[i].position(); j++) {
                        renderedQuads += piCount[i].get(j) / 4;
                    }
                }
            }
            piFirst[i].flip();
            piCount[i].flip();
        }
    }
    
    private boolean shouldRenderMesh(Mesh mesh) {
        if(mesh.visible && (Config.maxMeshesPerFrame == -1 || renderedMeshes < Config.maxMeshesPerFrame)) {
            if((!Config.renderFog && !Config.fogOcclusionWithoutFog)
                    || Config.fogOcclusion == !Config.fogOcclusion
                    || mesh.distSq(
                            eyePosX / 16.0,
                            mesh.y + 0.5,
                            eyePosZ / 16.0)
                        < Math.pow((fogStartEnd.get(1)) / 16.0 + 1.0, 2)) {
                return true;
            }
        }
        return false;
    }
    
    private void mainLoop() {
        if(Minecraft.getMinecraft().playerController.netClientHandler.doneLoadingTerrain) {
            for(Iterator<Entry<ChunkCoordIntPair, NeoRegion>> it = loadedRegionsMap.entrySet().iterator(); it.hasNext();) {
                Entry<ChunkCoordIntPair, NeoRegion> kv = it.next();
                NeoRegion v = kv.getValue();
                
                if(v.shouldDelete()) {
                    v.destroy();
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
            if(Keyboard.isKeyDown(Keyboard.KEY_RIGHT) && !wasDown[Keyboard.KEY_RIGHT]) {
                if(rendererSpeedup == 0) {
                    rendererSpeedup = 300;
                } else {
                    rendererSpeedup = 0;
                }
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
    Matrix4f modelViewMatrix = new Matrix4f();
    Matrix4f modelViewMatrixInv = new Matrix4f();
    Matrix4f projMatrix = new Matrix4f();
    
    private void render(int pass, double alpha) {
        int shader = getShaderProgram(pass);
        
        if(shader == 0) return;
        
        glBindVertexArray(VAO);    
        glUseProgram(shader);
        updateUniforms(alpha, pass);
        
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        glMultiDrawArrays(GL_QUADS, piFirst[pass], piCount[pass]);
        if(Config.wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    private void updateGLValues() {
        glGetFloat(GL_MODELVIEW_MATRIX, modelView);
        
        glGetFloat(GL_PROJECTION_MATRIX, projBuf);
        
        glGetInteger(GL_VIEWPORT, viewportBuf);
        
        projMatrix.load(projBuf);
        projBuf.flip();
        projMatrix.invert();
        projMatrix.store(projInvBuf);
        projInvBuf.flip();
        
        modelViewMatrix.load(modelView);
        modelView.flip();
        modelViewMatrixInv.load(modelViewMatrix).invert();
        
        fogColorBuf.limit(16);
        glGetFloat(GL_FOG_COLOR, fogColorBuf);
        fogColorBuf.limit(4);
        
        fogStartEnd.put(glGetFloat(GL_FOG_START));
        fogStartEnd.put(glGetFloat(GL_FOG_END));
        
        fogStartEnd.flip();
    }
    
    private void updateUniforms(double alpha, int pass) {
        int shaderProgram = getShaderProgram(pass);
        
        int u_modelView = glGetUniformLocation(shaderProgram, "modelView");
        int u_proj = glGetUniformLocation(shaderProgram, "proj");
        int u_playerPos = glGetUniformLocation(shaderProgram, "playerPos");
        int u_light = glGetUniformLocation(shaderProgram, "lightTex");
        int u_viewport = glGetUniformLocation(shaderProgram, "viewport");
        int u_projInv = glGetUniformLocation(shaderProgram, "projInv");
        int u_fogColor = glGetUniformLocation(shaderProgram, "fogColor");
        int u_fogStartEnd = glGetUniformLocation(shaderProgram, "fogStartEnd");
        int u_fogMode = glGetUniformLocation(shaderProgram, "fogMode");
        int u_fogDensity = glGetUniformLocation(shaderProgram, "fogDensity");
        
        glUniformMatrix4(u_modelView, false, modelView);
        glUniformMatrix4(u_proj, false, projBuf);
        glUniformMatrix4(u_projInv, false, projInvBuf);
        glUniform4f(u_viewport, viewportBuf.get(0),viewportBuf.get(1),viewportBuf.get(2),viewportBuf.get(3));
        glUniform4(u_fogColor, fogColorBuf);
        glUniform2(u_fogStartEnd, fogStartEnd);
        glUniform1i(u_fogMode, glGetInteger(GL_FOG_MODE));
        glUniform1f(u_fogDensity, glGetFloat(GL_FOG_DENSITY));
        
        glUniform3f(u_playerPos, (float)eyePosX, (float)eyePosY, (float)eyePosZ);
        
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
        for(int hasFog = 0; hasFog <= 1; hasFog++) {
            Set<String> defines = new HashSet<>();
            if(hasFog == 1) {
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
                ((hasFog == 1) ? shaderProgramsFog : shaderProgramsNoFog)[pass] = newShaderProgram;
            }
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
    }
       
    public void reloadShader() {
        reloadShader(0);
        reloadShader(1);
    }
    
    public void destroy() {
        if(!hasInited) return;
        
        glDeleteProgram(shaderProgramsFog[0]);
        glDeleteProgram(shaderProgramsFog[1]);
        glDeleteProgram(shaderProgramsNoFog[0]);
        glDeleteProgram(shaderProgramsNoFog[1]);
        glDeleteVertexArrays(VAO);
        mem.destroy();
        
        ChunkMesh.instances = 0;
        ChunkMesh.usedRAM = 0;
    }
    
    public void onWorldRendererChanged(WorldRenderer wr, WorldRendererChange change) {
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        NeoChunk neoChunk = getNeoChunk(x, z);
        
        neoChunk.isSectionVisible[y] = change == WorldRendererChange.VISIBLE;
        if(change == WorldRendererChange.DELETED) {
            removeMesh(neoChunk.chunkMeshes[y]);
            if(neoChunk.chunkMeshes[y] != null) {
                neoChunk.chunkMeshes[y].destroy();
                neoChunk.chunkMeshes[y] = null;
                neoChunk.region.meshes--;
            }
        }
        neoChunkChanged(neoChunk);
    }
    
    public void onWorldRendererPost(WorldRenderer wr, boolean sort) {
        int x = Math.floorDiv(wr.posX, 16);
        int y = Math.floorDiv(wr.posY, 16);
        int z = Math.floorDiv(wr.posZ, 16);
        
        if(Minecraft.getMinecraft().theWorld.getChunkFromChunkCoords(x, z).isChunkLoaded) {
            NeoChunk neoChunk = getNeoChunk(x, z);
            neoChunk.isSectionVisible[y] = ((IWorldRenderer)wr).isDrawn();
            neoChunk.putChunkMeshes(y, ((IWorldRenderer)wr).getChunkMeshes(), sort);
        }
    }
    
    public void onRenderFog() {
        forceRenderFog = false;
    }
    
    private NeoChunk getNeoChunk(int chunkX, int chunkZ) {
        return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
    }
    
    private NeoRegion getRegionContaining(int chunkX, int chunkZ) {
        ChunkCoordIntPair key = new ChunkCoordIntPair(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ, 32));
        NeoRegion region = loadedRegionsMap.get(key);
        if(region == null) {
            region = NeoRegion.load(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ , 32));
            loadedRegionsMap.put(key, region);
        }
        return region;
    }
    
    public void setVisible(NeoChunk chunk, boolean visible) {
        setVisible(chunk, visible, false);
    }
    
    public void setVisible(NeoChunk neoChunk, boolean visible, boolean forceCheck) {
        if(!forceCheck && visible == neoChunk.visible) return;
        
        neoChunk.visible = visible;
        neoChunkChanged(neoChunk);
    }
    
    public void neoChunkChanged(NeoChunk neoChunk) {
        int newLOD = neoChunk.hasChunkMeshes() ? 2 : 0;
        for(int y = 0; y < 16; y++) {
            for(int pass = 0; pass < 2; pass++) {
                ChunkMesh cm = neoChunk.chunkMeshes[y * 2 + pass];
                if(cm != null) {
                    if(neoChunk.isSectionVisible[y] && newLOD == 2) {
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
    
    public List<String> getDebugText() {
        List<String> text = new ArrayList<>();
        text.addAll(Arrays.asList(
                (!rendererActive ? EnumChatFormatting.RED + "(OFF) " : "") + "Neodymium " + Neodymium.VERSION
        ));
        text.addAll(mem.getDebugText());
        text.addAll(Arrays.asList(
                "Meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Rendered: " + renderedMeshes + " (" + renderedQuads / 1000 + "KQ)"
        ));
        if(rendererSpeedup > 0) {
            text.addAll(Arrays.asList(
                EnumChatFormatting.YELLOW + "(!) Renderer speedup active"
            ));
        }
        return text;
    }
    
    private int getShaderProgram(int pass) {
        return ((forceRenderFog || Config.renderFog) ? shaderProgramsFog : shaderProgramsNoFog)[pass];
    }
    
    private boolean shouldRenderInWorld(World world) {
        return world != null;
    }
    
    public static class NeoChunkComparator implements Comparator<NeoChunk> {
        Entity player;
        
        public NeoChunkComparator(Entity player) {
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