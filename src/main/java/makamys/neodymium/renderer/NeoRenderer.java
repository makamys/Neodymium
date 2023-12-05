package makamys.neodymium.renderer;

import lombok.val;
import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.IWorldRenderer;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.Map.Entry;

import static makamys.neodymium.Constants.VERSION;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.glMultiDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * The main renderer class.
 */
public class NeoRenderer {

    public boolean hasInited = false;
    public boolean destroyPending;
    public boolean reloadPending;
    public int rendererSpeedup;

    private static boolean[] wasDown = new boolean[256];

    public boolean renderWorld;
    public boolean rendererActive;
    private boolean showMemoryDebugger;

    public boolean forceRenderFog;
    public boolean hasIncompatibilities;

    private boolean fogEnabled;

    private static int MAX_MESHES;

    private int VAO;
    private int[] shaderProgramsFog = {0, 0};
    private int[] shaderProgramsNoFog = {0, 0};
    private IntBuffer[] piFirst = new IntBuffer[2];
    private IntBuffer[] piCount = new IntBuffer[2];
    GPUMemoryManager mem;
    private AttributeSet attributes;

    private Map<ChunkCoordIntPair, NeoRegion> loadedRegionsMap = new HashMap<>();
    private List<NeoRegion> loadedRegionsList = new ArrayList<>();

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

    public NeoRenderer(World world) {
        this.world = world;
        if (shouldRenderInWorld(world)) {
            hasInited = init();
        }

        renderWorld = true;
        rendererActive = true;
    }

    Vector4f transformedOrigin = new Vector4f();

    public void preRenderSortedRenderers(int renderPass, double alpha, WorldRenderer[] sortedWorldRenderers) {
        if (hasInited) {
            if (renderPass == 0) {
                renderedMeshes = 0;
                renderedQuads = 0;

                mainLoop();
                if (Minecraft.getMinecraft().currentScreen == null) {
                    handleKeyboard();
                }
                if (mem.getCoherenceRate() < 0.95f || frameCount % 4 == 0) {
                    mem.runGC(false);
                }

                if (rendererActive && renderWorld) {
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

                    eyePosXTDiv = Math.floorDiv((int) Math.floor(eyePosXT), 16);
                    eyePosYTDiv = Math.floorDiv((int) Math.floor(eyePosYT), 16);
                    eyePosZTDiv = Math.floorDiv((int) Math.floor(eyePosZT), 16);

                    sort(frameCount % 100 == 0, frameCount % Config.sortFrequency == 0);

                    initIndexBuffers();
                }

                frameCount++;
            }

            if (rendererActive && renderWorld) {
                Minecraft.getMinecraft().entityRenderer.enableLightmap((double) alpha);

                render(renderPass, alpha);

                Minecraft.getMinecraft().entityRenderer.disableLightmap((double) alpha);
            }
        }
    }

    public void onRenderTickEnd() {
        if (Neodymium.isActive()) {
            if (reloadPending) {
                Minecraft.getMinecraft().renderGlobal.loadRenderers();
            }
            if (showMemoryDebugger && mem != null) {
                GuiHelper.begin();
                mem.drawInfo();
                GuiHelper.end();
            }
        } else if (destroyPending) {
            destroy();
            destroyPending = false;
            Neodymium.renderer = null;
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }

    private void sort(boolean pass0, boolean pass1) {
        for (NeoRegion r : loadedRegionsMap.values()) {
            r.getRenderData().sort(eyePosX, eyePosY, eyePosZ, pass0, pass1);
        }
    }

    private void initIndexBuffers() {
        loadedRegionsList.clear();
        loadedRegionsList.addAll(loadedRegionsMap.values());
        loadedRegionsList.sort(Comparators.REGION_DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ));

        for (int i = 0; i < 2; i++) {
            piFirst[i].limit(MAX_MESHES);
            piCount[i].limit(MAX_MESHES);
            int order = i == 0 ? 1 : -1;
            for (int regionI = order == 1 ? 0 : loadedRegionsList.size() - 1; regionI >= 0 && regionI < loadedRegionsList.size(); regionI += order) {
                NeoRegion.RenderData region = loadedRegionsList.get(regionI).getRenderData();
                region.batchFirst[i] = piFirst[i].position();
                for (Mesh mesh : region.getSentMeshes(i)) {
                    WorldRenderer wr = ((ChunkMesh) mesh).wr;
                    if (mesh.visible && wr.isVisible && shouldRenderMesh(mesh)) {
                        int meshes = mesh.writeToIndexBuffer(piFirst[i], piCount[i], eyePosXTDiv, eyePosYTDiv, eyePosZTDiv, i);
                        renderedMeshes += meshes;
                        for (int j = piCount[i].position() - meshes; j < piCount[i].position(); j++) {
                            renderedQuads += piCount[i].get(j) / 4;
                        }
                    }
                }
                region.batchLimit[i] = piFirst[i].position();
            }
            piFirst[i].flip();
            piCount[i].flip();
        }
    }

    private boolean shouldRenderMesh(Mesh mesh) {
        if ((Config.maxMeshesPerFrame == -1 || renderedMeshes < Config.maxMeshesPerFrame)) {
            if ((!isFogEnabled() && !Config.fogOcclusionWithoutFog)
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
        if (Minecraft.getMinecraft().playerController.netClientHandler.doneLoadingTerrain) {
            for (Iterator<Entry<ChunkCoordIntPair, NeoRegion>> it = loadedRegionsMap.entrySet().iterator(); it.hasNext(); ) {
                Entry<ChunkCoordIntPair, NeoRegion> kv = it.next();
                NeoRegion v = kv.getValue();

                if (v.shouldDelete()) {
                    v.destroy();
                    it.remove();
                } else {
                    v.tick();
                }
            }
        }
    }

    private void handleKeyboard() {
        if (Config.debugPrefix == 0 || (Config.debugPrefix != -1 && Keyboard.isKeyDown(Config.debugPrefix))) {
            if (CheatHelper.canCheat()) {
                if (Keyboard.isKeyDown(Keyboard.KEY_F) && !wasDown[Keyboard.KEY_F]) {
                    rendererActive = !rendererActive;
                }
                if (Keyboard.isKeyDown(Keyboard.KEY_V) && !wasDown[Keyboard.KEY_V]) {
                    renderWorld = !renderWorld;
                }
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_R) && !wasDown[Keyboard.KEY_R]) {
                reloadShader();
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_M) && !wasDown[Keyboard.KEY_M]) {
                showMemoryDebugger = !showMemoryDebugger;
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_P) && !wasDown[Keyboard.KEY_P]) {
                Util.dumpTexture();
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_LEFT) && !wasDown[Keyboard.KEY_LEFT]) {
                reloadPending = true;
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT) && !wasDown[Keyboard.KEY_RIGHT]) {
                if (rendererSpeedup == 0) {
                    rendererSpeedup = 300;
                } else {
                    rendererSpeedup = 0;
                }
            }
        }
        for (int i = 0; i < 256; i++) {
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

        if (shader == 0) return;

        glBindVertexArray(VAO);

        if (!Compat.isShaders()) {
            glUseProgram(shader);
            updateUniforms(alpha, pass);
        }

        if (isWireframeEnabled()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }

        int u_renderOffset = -1;
        if (!Compat.isShaders()) {
            u_renderOffset = glGetUniformLocation(getShaderProgram(pass), "renderOffset");
        }

        int oldLimit = piFirst[pass].limit();

        int order = pass == 0 ? 1 : -1;
        for (int regionI = order == 1 ? 0 : loadedRegionsList.size() - 1; regionI >= 0 && regionI < loadedRegionsList.size(); regionI += order) {
            NeoRegion.RenderData region = loadedRegionsList.get(regionI).getRenderData();
            Util.setPositionAndLimit(piFirst[pass], region.batchFirst[pass], region.batchLimit[pass]);
            Util.setPositionAndLimit(piCount[pass], region.batchFirst[pass], region.batchLimit[pass]);

            if (Compat.isShaders()) {
                GL11.glMatrixMode(GL_MODELVIEW);

                val offsetX = (float) (region.originX - eyePosX);
                val offsetY = (float) ((region.originY - eyePosY) + 0.12);
                val offsetZ = (float) (region.originZ - eyePosZ);

                GL11.glPushMatrix();
                GL11.glTranslatef(offsetX, offsetY, offsetZ);
            } else {
                glUniform3f(u_renderOffset, (float) (region.originX - eyePosX), (float) (region.originY - eyePosY), (float) (region.originZ - eyePosZ));
            }

            glMultiDrawArrays(GL_QUADS, piFirst[pass], piCount[pass]);

            if (Compat.isShaders())
                GL11.glPopMatrix();
        }
        Util.setPositionAndLimit(piFirst[pass], 0, oldLimit);
        Util.setPositionAndLimit(piCount[pass], 0, oldLimit);

        if (isWireframeEnabled()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }

        if (!Compat.isShaders()) {
            glUseProgram(0);
        }

        glBindVertexArray(0);
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

        fogEnabled = GL11.glIsEnabled(GL11.GL_FOG) && !OFUtil.isFogOff();
    }

    private void updateUniforms(double alpha, int pass) {
        int shaderProgram = getShaderProgram(pass);

        int u_modelView = glGetUniformLocation(shaderProgram, "modelView");
        int u_proj = glGetUniformLocation(shaderProgram, "proj");
        int u_playerPos = glGetUniformLocation(shaderProgram, "playerPos");
        int u_light = 0, u_light_r = 0, u_light_g = 0, u_light_b = 0;
        if (Compat.RPLE()) {
            u_light_r = glGetUniformLocation(shaderProgram, "lightTexR");
            u_light_g = glGetUniformLocation(shaderProgram, "lightTexG");
            u_light_b = glGetUniformLocation(shaderProgram, "lightTexB");
        } else {
            u_light = glGetUniformLocation(shaderProgram, "lightTex");
        }
        int u_viewport = glGetUniformLocation(shaderProgram, "viewport");
        int u_projInv = glGetUniformLocation(shaderProgram, "projInv");
        int u_fogColor = glGetUniformLocation(shaderProgram, "fogColor");
        int u_fogStartEnd = glGetUniformLocation(shaderProgram, "fogStartEnd");
        int u_fogMode = glGetUniformLocation(shaderProgram, "fogMode");
        int u_fogDensity = glGetUniformLocation(shaderProgram, "fogDensity");

        glUniformMatrix4(u_modelView, false, modelView);
        glUniformMatrix4(u_proj, false, projBuf);
        glUniformMatrix4(u_projInv, false, projInvBuf);
        glUniform4f(u_viewport, viewportBuf.get(0), viewportBuf.get(1), viewportBuf.get(2), viewportBuf.get(3));
        glUniform4(u_fogColor, fogColorBuf);
        glUniform2(u_fogStartEnd, fogStartEnd);
        glUniform1i(u_fogMode, glGetInteger(GL_FOG_MODE));
        glUniform1f(u_fogDensity, glGetFloat(GL_FOG_DENSITY));

        glUniform3f(u_playerPos, (float) eyePosX, (float) eyePosY, (float) eyePosZ);

        if (Compat.RPLE()) {
            //TODO connect to RPLE gl api (once that exists)
            // For now we just use the RPLE default texture indices
            glUniform1i(u_light_r, 1);
            glUniform1i(u_light_g, 2);
            glUniform1i(u_light_b, 3);
        } else {
            glUniform1i(u_light, 1);
        }

        modelView.position(0);
        projBuf.position(0);
        viewportBuf.position(0);
        projInvBuf.position(0);
        fogColorBuf.position(0);
        fogStartEnd.position(0);
    }

    /**
     * @implSpec The attributes here need to be kept in sync with {@link MeshQuad#writeToBuffer(BufferWriter)}
     */
    public boolean init() {
        // The average mesh is 60 KB. Let's be safe and assume 8 KB per mesh.
        // This means 1 MB of index data per 512 MB of VRAM.
        MAX_MESHES = Config.VRAMSize * 128;

        Compat.updateShadersState();

        attributes = new AttributeSet();
        attributes.addAttribute("POS", 3, 4, GL_FLOAT);
        if (Config.shortUV) {
            attributes.addAttribute("TEXTURE", 2, 2, GL_UNSIGNED_SHORT);
        } else {
            attributes.addAttribute("TEXTURE", 2, 4, GL_FLOAT);
        }
        attributes.addAttribute("COLOR", 4, 1, GL_UNSIGNED_BYTE);
        attributes.addAttribute("BRIGHTNESS", 2, 2, GL_SHORT);
        if (Compat.isShaders()) {
            attributes.addAttribute("ENTITY_DATA_1", 1, 4, GL_UNSIGNED_INT);
            attributes.addAttribute("ENTITY_DATA_2", 1, 4, GL_UNSIGNED_INT);
            attributes.addAttribute("NORMAL", 3, 4, GL_FLOAT);
            attributes.addAttribute("TANGENT", 4, 4, GL_FLOAT);
            attributes.addAttribute("MIDTEXTURE", 2, 4, GL_FLOAT);
        } else if (Compat.RPLE()) {
            attributes.addAttribute("BRIGHTNESS_RED", 2, 2, GL_SHORT);
            attributes.addAttribute("BRIGHTNESS_GREEN", 2, 2, GL_SHORT);
            attributes.addAttribute("BRIGHTNESS_BLUE", 2, 2, GL_SHORT);
        }

        reloadShader();

        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);

        mem = new GPUMemoryManager();

        glBindBuffer(GL_ARRAY_BUFFER, mem.VBO);
        
        // position   3 floats 12 bytes offset 0
        // texture    2 floats  8 bytes offset 12
        // color      4 bytes   4 bytes offset 20
        // brightness 2 shorts  4 bytes offset 24
        // entitydata 3 shorts  6 bytes offset 28
        // <padding>            2 bytes
        // normal     3 floats 12 bytes offset 36
        // tangent    4 floats 16 bytes offset 48
        // midtexture 2 floats  8 bytes offset 64
        if (Compat.isShaders()) {
            val stride = 72;
            val entityAttrib = 10;
            val midTexCoordAttrib = 11;
            val tangentAttrib = 12;

            // position   3 floats 12 bytes offset 0
            GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

            // texture    2 floats  8 bytes offset 12
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 12);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            // color      4 bytes   4 bytes offset 20
            GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, 20);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

            // brightness 2 shorts  4 bytes offset 24
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

            // entitydata 3 shorts  6 bytes offset 28
            GL20.glVertexAttribPointer(entityAttrib, 3, GL11.GL_SHORT, false, stride, 28);
            GL20.glEnableVertexAttribArray(entityAttrib);

            // normal     3 floats 12 bytes offset 36
            GL11.glNormalPointer(GL11.GL_FLOAT, stride, 36);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);

            // tangent    4 floats 16 bytes offset 48
            GL20.glVertexAttribPointer(tangentAttrib, 4, GL11.GL_FLOAT, false, stride, 48);
            GL20.glEnableVertexAttribArray(tangentAttrib);

            // midtexture 2 floats  8 bytes offset 64
            GL13.glClientActiveTexture(GL13.GL_TEXTURE3);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 64);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

            ARBVertexShader.glVertexAttribPointerARB(midTexCoordAttrib, 2, GL11.GL_FLOAT, false, stride, 64);
            ARBVertexShader.glEnableVertexAttribArrayARB(midTexCoordAttrib);
        } else {
            attributes.enable();
        }

        for (int i = 0; i < 2; i++) {
            piFirst[i] = BufferUtils.createIntBuffer(MAX_MESHES);
            piFirst[i].flip();
            piCount[i] = BufferUtils.createIntBuffer(MAX_MESHES);
            piCount[i].flip();
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        return true;
    }

    public int getStride() {
        return attributes.stride();
    }

    public void reloadShader(int pass, AttributeSet attributeSet) {
        for (int hasFog = 0; hasFog <= 1; hasFog++) {
            Map<String, String> defines = new HashMap<>();
            if (hasFog == 1) {
                defines.put("RENDER_FOG", "");
            }
            if (Config.shortUV) {
                defines.put("SHORT_UV", "");
            }
            if (Compat.RPLE()) {
                defines.put("RPLE", "");
            }
            if (pass == 0) {
                defines.put("PASS_0", "");
            }

            attributeSet.addDefines(defines);

            boolean errors = false;

            int vertexShader;
            vertexShader = glCreateShader(GL_VERTEX_SHADER);

            glShaderSource(vertexShader, Preprocessor.preprocess(Util.readFile("shaders/chunk.vert"), defines));
            glCompileShader(vertexShader);

            if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling vertex shader: " + glGetShaderInfoLog(vertexShader, 256));
                errors = true;
            }

            int fragmentShader;
            fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);

            glShaderSource(fragmentShader, Preprocessor.preprocess(Util.readFile("shaders/chunk.frag"), defines));
            glCompileShader(fragmentShader);

            if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == 0) {
                System.out.println("Error compiling fragment shader: " + glGetShaderInfoLog(fragmentShader, 256));
                errors = true;
            }

            int newShaderProgram = glCreateProgram();
            glAttachShader(newShaderProgram, vertexShader);
            glAttachShader(newShaderProgram, fragmentShader);
            glLinkProgram(newShaderProgram);

            if (glGetProgrami(newShaderProgram, GL_LINK_STATUS) == 0) {
                System.out.println("Error linking shader: " + glGetShaderInfoLog(newShaderProgram, 256));
                errors = true;
            }

            if (!errors) {
                ((hasFog == 1) ? shaderProgramsFog : shaderProgramsNoFog)[pass] = newShaderProgram;
            }

            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
    }

    public void reloadShader() {
        reloadShader(0, attributes);
        reloadShader(1, attributes);
    }

    public void destroy() {
        if (!hasInited) return;

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
        if (change == WorldRendererChange.DELETED) {
            removeMesh(neoChunk.chunkMeshes[y]);
            if (neoChunk.chunkMeshes[y] != null) {
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

        if (Minecraft.getMinecraft().theWorld.getChunkFromChunkCoords(x, z).isChunkLoaded) {
            NeoChunk neoChunk = getNeoChunk(x, z);
            neoChunk.isSectionVisible[y] = ((IWorldRenderer) wr).isDrawn();
            neoChunk.putChunkMeshes(y, ((IWorldRenderer) wr).getChunkMeshes(), sort);
        }
    }

    public void onRenderFog() {
        forceRenderFog = false;
    }

    private NeoChunk getNeoChunk(int chunkX, int chunkZ) {
        return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
    }

    private NeoRegion getRegionContaining(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, NeoRegion.SIZE);
        int regionZ = Math.floorDiv(chunkZ, NeoRegion.SIZE);
        ChunkCoordIntPair key = new ChunkCoordIntPair(regionX, regionZ);

        NeoRegion region = loadedRegionsMap.get(key);
        if (region == null) {
            region = NeoRegion.load(regionX, regionZ);
            loadedRegionsMap.put(key, region);
        }
        return region;
    }

    public void setVisible(NeoChunk chunk, boolean visible) {
        setVisible(chunk, visible, false);
    }

    public void setVisible(NeoChunk neoChunk, boolean visible, boolean forceCheck) {
        if (!forceCheck && visible == neoChunk.visible) return;

        neoChunk.visible = visible;
        neoChunkChanged(neoChunk);
    }

    public void neoChunkChanged(NeoChunk neoChunk) {
        int newLOD = neoChunk.hasChunkMeshes() ? 2 : 0;
        for (int y = 0; y < 16; y++) {
            for (int pass = 0; pass < 2; pass++) {
                ChunkMesh cm = neoChunk.chunkMeshes[y * 2 + pass];
                if (cm != null) {
                    if (neoChunk.isSectionVisible[y] && newLOD == 2) {
                        if (!cm.visible) {
                            setMeshVisible(cm, true);
                        }
                    } else {
                        if (cm.visible) {
                            setMeshVisible(cm, false);
                        }
                    }
                }
            }
        }
    }

    protected void setMeshVisible(Mesh mesh, boolean visible) {
        if (mesh == null) return;

        if (mesh.visible != visible) {
            mesh.visible = visible;

            if (mesh.gpuStatus == GPUStatus.UNSENT) {
                mem.sendMeshToGPU(mesh);
                NeoRegion region = getRegionContaining(mesh.x, mesh.z);
                region.getRenderData().getSentMeshes(mesh.pass).add(mesh);
                mesh.containingRegion = region;
            }
        }
    }

    public void removeMesh(Mesh mesh) {
        if (mesh == null) return;

        mem.deleteMeshFromGPU(mesh);
        if (mesh.containingRegion != null) {
            mesh.containingRegion.getRenderData().getSentMeshes(mesh.pass).remove(mesh);
        }
        setMeshVisible(mesh, false);
    }

    public List<String> getDebugText(boolean statusCommand) {
        List<String> text = new ArrayList<>();
        text.add(
                (!rendererActive ? EnumChatFormatting.RED + "(OFF) " : "")
                + (statusCommand ? EnumChatFormatting.LIGHT_PURPLE : "")
                + "Neodymium " + VERSION
        );
        text.addAll(mem.getDebugText());
        text.addAll(Arrays.asList(
                "Meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
                "Rendered: " + renderedMeshes + " (" + renderedQuads / 1000 + "KQ)"
        ));
        if (rendererSpeedup > 0) {
            text.add(EnumChatFormatting.YELLOW + "(!) Renderer speedup active");
        }
        if (hasIncompatibilities) {
            text.add(EnumChatFormatting.YELLOW + "(!) Incompatibilities");
            if (!statusCommand) {
                text.add(EnumChatFormatting.YELLOW + "Type '/neodymium status'");
            }
        }
        return text;
    }

    private int getShaderProgram(int pass) {
        return ((forceRenderFog || isFogEnabled()) ? shaderProgramsFog : shaderProgramsNoFog)[pass];
    }

    private boolean isFogEnabled() {
        switch (Config.renderFog) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            default:
                return fogEnabled;
        }
    }

    private boolean shouldRenderInWorld(World world) {
        return world != null;
    }

    private static boolean isWireframeEnabled() {
        return Config.wireframe && CheatHelper.canCheat();
    }

    public static enum WorldRendererChange {
        VISIBLE, INVISIBLE, DELETED
    }
}
