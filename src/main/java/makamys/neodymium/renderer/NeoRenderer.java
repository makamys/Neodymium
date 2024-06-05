package makamys.neodymium.renderer;

import lombok.val;
import lombok.var;
import makamys.neodymium.Compat;
import makamys.neodymium.Neodymium;
import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.NeodymiumWorldRenderer;
import makamys.neodymium.renderer.Mesh.GPUStatus;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.*;
import makamys.neodymium.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import static makamys.neodymium.Constants.VERSION;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.glMultiDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

/**
 * The main renderer class.
 */
public class NeoRenderer {

    public boolean hasInited = false;
    public boolean isFirstPass = true;
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

    private int[] shaderProgramsFog = {0, 0};
    private int[] shaderProgramsNoFog = {0, 0};
    private List<GPUMemoryManager> mems = new ArrayList<>();
    private Map<Integer, List<GPUMemoryManager>> memMap = new HashMap<>();
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

    private int renderedMeshesRender, renderedQuadsRender;
    private int renderedMeshesShadow, renderedQuadsShadow;
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


    private int gcCounter = 0;
    public int preRenderSortedRenderers(int renderPass, double alpha, WorldRenderer[] sortedWorldRenderers) {
        if (!hasInited)
            return 0;

        val mc = Minecraft.getMinecraft();
        val opaquePass = renderPass == 0;
        val isShadowPass = Compat.isShadersShadowPass();
        val shouldRender = rendererActive && renderWorld;

        if (isFirstPass) {
            renderedMeshesRender = 0;
            renderedQuadsRender = 0;
            renderedMeshesShadow = 0;
            renderedQuadsShadow = 0;

            mainLoop();
            if (mc.currentScreen == null)
                handleKeyboard();
        }

        var rendered = 0;
        if (shouldRender) {
            if (opaquePass) {
                updateGLValues();
                updateEyePos(alpha);

                if (!isShadowPass)
                    sortMeshes(frameCount % 100 == 0, frameCount % Config.sortFrequency == 0);

                initIndexBuffers(isShadowPass);
            }

            if (isFirstPass && !Compat.keepRenderListLogic() && !Compat.isFalseTweaksModPresent())
                updateRenderGlobalStats();

            rendered = render(renderPass, alpha);
        }

        isFirstPass = false;
        return rendered;
    }

    private void updateEyePos(double alpha) {
        transformedOrigin.set(0, 0, 0, 1);
        Matrix4f.transform(modelViewMatrixInv, transformedOrigin, transformedOrigin);

        val rve = Minecraft.getMinecraft().renderViewEntity;

        eyePosX = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * alpha;
        eyePosY = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * alpha + rve.getEyeHeight();
        eyePosZ = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * alpha;

        eyePosXT = eyePosX + transformedOrigin.x;
        eyePosYT = eyePosY + transformedOrigin.y;
        eyePosZT = eyePosZ + transformedOrigin.z;

        eyePosXTDiv = Math.floorDiv((int) Math.floor(eyePosXT), 16);
        eyePosYTDiv = Math.floorDiv((int) Math.floor(eyePosYT), 16);
        eyePosZTDiv = Math.floorDiv((int) Math.floor(eyePosZT), 16);
    }

    public void onRenderTickEnd() {
        if (Neodymium.isActive()) {
            if (reloadPending) {
                Minecraft.getMinecraft().renderGlobal.loadRenderers();
                return;
            }

            if (gcCounter % 4 == 0) {
                for (val mem : mems)
                    mem.runGC(false);
            }
            gcCounter++;

            if (showMemoryDebugger) {
                int yOff = 20;
                boolean drawing = false;
                for (val mem : mems) {
                    if (mem != null) {
                        if (!drawing) {
                            drawing = true;
                            GuiHelper.begin();
                        }
                        yOff = mem.drawDebugInfo(yOff) + 10;
                    }
                }
                if (drawing)
                    GuiHelper.end();
            }

            isFirstPass = true;
        } else if (destroyPending) {
            destroy();
            destroyPending = false;
            Neodymium.renderer = null;
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }

    private void sortMeshes(boolean pass0, boolean pass1) {
        for (val mem: mems) {
            for (NeoRegion r : loadedRegionsMap.values()) {
                r.getRenderData(mem).sort(eyePosX, eyePosY, eyePosZ, pass0, pass1);
            }
        }
    }

    private boolean isRendererVisible(WorldRenderer wr, boolean shadowPass) {
        return shadowPass || wr.isVisible;
    }

    private void initIndexBuffers(boolean shadowPass) {
        loadedRegionsList.clear();
        loadedRegionsList.addAll(loadedRegionsMap.values());
        loadedRegionsList.sort(Comparators.REGION_DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ));

        for (val mem: mems) {
            mem.piFirst.clear();
            mem.piCount.clear();
            int order = mem.pass == 0 ? 1 : -1;
            for (int regionI = order == 1 ? 0 : loadedRegionsList.size() - 1; regionI >= 0 && regionI < loadedRegionsList.size(); regionI += order) {
                NeoRegion.RenderData region = loadedRegionsList.get(regionI).getRenderData(mem);
                region.batchFirst = mem.piFirst.position();
                for (Mesh mesh : region.getSentMeshes()) {
                    WorldRenderer wr = ((ChunkMesh) mesh).wr;
                    if ((shadowPass || wr.isInFrustum) && mesh.visible && isRendererVisible(wr, shadowPass) && shouldRenderMesh(mesh)) {
                        if (mem.piFirst.position() >= mem.piFirst.limit() - 16) {
                            mem.growIndexBuffers();
                        }
                        int meshes = mesh.writeToIndexBuffer(mem.piFirst, mem.piCount, eyePosXTDiv, eyePosYTDiv, eyePosZTDiv, mem.pass);
                        if (shadowPass) {
                            renderedMeshesShadow += meshes;
                        } else {
                            renderedMeshesRender += meshes;
                        }
                        for (int j = mem.piCount.position() - meshes; j < mem.piCount.position(); j++) {
                            val count = mem.piCount.get(j) / 4;
                            if (shadowPass) {
                                renderedQuadsShadow += count;
                            } else {
                                renderedQuadsRender += count;
                            }
                        }
                    }
                    if(Compat.isSpeedupAnimationsEnabled() && !Compat.keepRenderListLogic()) {
                        // Hodgepodge hooks this method to decide what animations to play, make sure it runs
                        wr.getGLCallListForPass(mem.pass);
                    }
                }
                region.batchLimit = mem.piFirst.position();
            }
            mem.piFirst.flip();
            mem.piCount.flip();
        }
    }

    private boolean shouldRenderMesh(Mesh mesh) {
        if (Compat.isShadersShadowPass())
            return true;
        if ((Config.maxMeshesPerFrame == -1 || renderedMeshesRender < Config.maxMeshesPerFrame)) {
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



    private static class DelayedTask implements Comparable<DelayedTask> {
        public final int timestamp;
        public final Runnable task;
        private final int idx;
        private static final AtomicInteger IIDX = new AtomicInteger();
        public DelayedTask(int timestamp, Runnable task) {
            this.timestamp = timestamp;
            this.task = task;
            idx = IIDX.getAndIncrement();
        }
        @Override
        public int compareTo(DelayedTask o) {
            if (timestamp == o.timestamp) {
                return Integer.compare(idx, o.idx);
            }
            return Integer.compare(timestamp, o.timestamp);
        }
    }
    private static int frameCounter = 0;
    private static final TreeSet<DelayedTask> tasks = new TreeSet<>();

    public static void submitTask(Runnable task, int delayFrames) {
        tasks.add(new DelayedTask(frameCounter + delayFrames, task));
    }

    private static void updateRenderGlobalStats() {
        // Normally renderSortedRenderers does this, but we cancelled it

        RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;

        for(WorldRenderer wr : rg.sortedWorldRenderers) {
            if(wr != null) {
                ++rg.renderersLoaded;
                if (wr.skipRenderPass[0]) {
                    ++rg.renderersSkippingRenderPass;
                } else if (!wr.isInFrustum) {
                    ++rg.renderersBeingClipped;
                } else if (rg.occlusionEnabled && !wr.isVisible) {
                    ++rg.renderersBeingOccluded;
                } else {
                    ++rg.renderersBeingRendered;
                }
            }
        }
    }

    private void mainLoop() {
        if (!tasks.isEmpty()) {
            val task = tasks.first();
            if (task.timestamp - frameCounter < 0) {
                tasks.pollFirst();
                task.task.run();
            }
        }
        frameCounter++;
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

    private int render(int pass, double alpha) {
        val mems = memMap.get(pass);
        if (mems == null)
            return 0;

        if (!Compat.isOptiFineShadersEnabled()) {
            int shader = getShaderProgram(pass);

            if (shader == 0) return 0;
            glUseProgram(shader);
            updateUniforms(alpha, pass);
        }

        if (isWireframeEnabled()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }

        int u_renderOffset = -1;
        if (!Compat.isOptiFineShadersEnabled()) {
            u_renderOffset = glGetUniformLocation(getShaderProgram(pass), "renderOffset");
        }

        val er = Minecraft.getMinecraft().entityRenderer;
        er.enableLightmap(alpha);

        var rendered = 0;
        for (val mem: mems) {
            glBindVertexArray(mem.VAO);
            int oldLimit = mem.piFirst.limit();

            int order = pass == 0 ? 1 : -1;
            for (int regionI = order == 1 ? 0 : loadedRegionsList.size() - 1; regionI >= 0 && regionI < loadedRegionsList.size(); regionI += order) {
                NeoRegion.RenderData region = loadedRegionsList.get(regionI).getRenderData(mem);
                rendered += region.batchLimit - region.batchFirst;
                Util.setPositionAndLimit(mem.piFirst, region.batchFirst, region.batchLimit);
                Util.setPositionAndLimit(mem.piCount, region.batchFirst, region.batchLimit);

                if (Compat.isOptiFineShadersEnabled()) {
                    GL11.glMatrixMode(GL_MODELVIEW);

                    val offsetX = (float) (region.originX - eyePosX);
                    val offsetY = (float) ((region.originY - eyePosY) + 0.12);
                    val offsetZ = (float) (region.originZ - eyePosZ);

                    GL11.glPushMatrix();
                    GL11.glTranslatef(offsetX, offsetY, offsetZ);
                } else {
                    glUniform3f(u_renderOffset, (float) (region.originX - eyePosX), (float) (region.originY - eyePosY), (float) (region.originZ - eyePosZ));
                }

                glMultiDrawArrays(GL_QUADS, mem.piFirst, mem.piCount);

                if (Compat.isOptiFineShadersEnabled())
                    GL11.glPopMatrix();
            }
            Util.setPositionAndLimit(mem.piFirst, 0, oldLimit);
            Util.setPositionAndLimit(mem.piCount, 0, oldLimit);
        }

        if (isWireframeEnabled()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }

        if (!Compat.isOptiFineShadersEnabled()) {
            glUseProgram(0);
        }

        glBindVertexArray(0);

        er.disableLightmap(alpha);
        return rendered;
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
        if (Compat.isRPLEModPresent()) {
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

        if (Compat.isRPLEModPresent()) {
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
     * @implSpec The attributes here need to be kept in sync with {@link RenderUtil#writeMeshQuadToBuffer(MeshQuad, BufferWriter, int)}
     */
    public boolean init() {
        Compat.updateOptiFineShadersState();

        attributes = new AttributeSet();

        Neodymium.util.initVertexAttributes(attributes);

        reloadShader();
        return true;
    }

    private GPUMemoryManager initMemoryManager(int pass) throws Exception {
        val mem = new GPUMemoryManager(mems.size(), pass);
        mems.add(mem);
        memMap.computeIfAbsent(pass, p -> new ArrayList<>()).add(mem);

        glBindVertexArray(mem.VAO);

        glBindBuffer(GL_ARRAY_BUFFER, mem.VBO);

        Neodymium.util.applyVertexAttributes(attributes);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        return mem;
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
            if (Compat.isRPLEModPresent()) {
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
        for (val mem: mems) {
            mem.destroy();
        }

        for (val region: loadedRegionsList) {
            region.destroy();
        }
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
            neoChunk.isSectionVisible[y] = ((NeodymiumWorldRenderer) wr).nd$isDrawn();
            neoChunk.putChunkMeshes(y, ((NeodymiumWorldRenderer) wr).nd$getChunkMeshes(), sort);
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
                    uploadMeshToGPU(cm);
                }
            }
        }
    }


    protected void uploadMeshToGPU(Mesh mesh) {
        if (mesh.gpuStatus != GPUStatus.UNSENT || mesh.buffer == null) {
            return;
        }
        boolean sent = false;
        GPUMemoryManager mem = null;
        val memArr = memMap.get(mesh.pass);
        if (memArr != null)
            for (int i = 0; i < memArr.size(); i++) {
                mem = memArr.get(i);
                sent = mem.uploadMesh(mesh);
                if (sent)
                    break;
            }
        if (!sent) {
            try {
                mem = initMemoryManager(mesh.pass);
            } catch (Exception e) {
                ChatUtil.showNeoChatMessage("Could not allocate memory buffer: " + e.getMessage(), ChatUtil.MessageVerbosity.ERROR);
                e.printStackTrace();
                Neodymium.renderer.destroyPending = true;
                return;
            }
            mem.uploadMesh(mesh);
        }
        NeoRegion region = getRegionContaining(mesh.x, mesh.z);
        region.getRenderData(mem).getSentMeshes().add(mesh);
        mesh.containingRegion = region;
    }

    protected void setMeshVisible(Mesh mesh, boolean visible) {
        if (mesh == null) return;

        if (mesh.visible != visible) {
            mesh.visible = visible;
        }
    }

    public void removeMesh(Mesh mesh) {
        if (mesh == null) return;

        val mem = mesh.attachedManager;
        if (mem != null) {
            mesh.attachedManager.deleteMesh(mesh);
            if (mesh.containingRegion != null) {
                mesh.containingRegion.getRenderData(mem).getSentMeshes().remove(mesh);
            }
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
        text.addAll(Arrays.asList(
                "Meshes: " + ChunkMesh.instances.get() + " (" + ChunkMesh.usedRAM.get() / 1024 / 1024 + "MB)",
                "Rendered: " + renderedMeshesRender + " (" + renderedQuadsRender / 1000 + "KQ)"
                                 ));
        if (Compat.isOptiFineShadersEnabled()) {
            text.add("Shadow Rendered: " + renderedMeshesShadow + " (" + renderedQuadsShadow / 1000 + "KQ)");
        }
        text.add("VRAM buffers:");
        for (int i = 0; i < mems.size(); i++) {
            val mem = mems.get(i);
            text.addAll(mem.debugText());
        }
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
