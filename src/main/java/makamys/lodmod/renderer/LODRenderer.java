package makamys.lodmod.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

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

import makamys.lodmod.ducks.IWorldRenderer;
import makamys.lodmod.util.Util;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class LODRenderer {

	private boolean hasInited = false;
	
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
    
    // TODO make these packets to make this work on dedicated servers
    Queue<Chunk> farChunks = new ConcurrentLinkedQueue<>();
    
    List<ChunkCoordIntPair> serverChunkLoadQueue = new ArrayList<>();
    
    private double lastSortX = Double.NaN;
    private double lastSortY = Double.NaN;
    private double lastSortZ = Double.NaN;
    
    private long lastGCTime = -1;
    private long gcInterval = 60 * 1000;
    
    public int renderRange = 48;
    
    private boolean freezeMeshes;
    
    public LODRenderer(){
        hasInited = init();
    }
    
    public void beforeRenderTerrain(float alpha) {
        if(hasInited) {
            mainLoop();
            handleKeyboard();
            gcInterval = 10 * 1000;
            if(lastGCTime == -1 || (System.currentTimeMillis() - lastGCTime) > gcInterval) {
                runGC();
                lastGCTime = System.currentTimeMillis();
            }
            
            if(renderLOD) {
                sort();
                initIndexBuffers();
                render(alpha);
            }
        }
    }
    
    private void sort() {
        Entity player = (Entity)Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList.get(0);
        sentMeshes[1].sort(new MeshDistanceComparator(player.posX, player.posY, player.posZ));
    }
    
    private void initIndexBuffers() {
        for(int i = 0; i < 2; i++) {
            piFirst[i].limit(sentMeshes[i].size() + sentMeshes[i].size());
            piCount[i].limit(sentMeshes[i].size() + sentMeshes[i].size());
            for(Mesh mesh : sentMeshes[i]) {
                if(mesh.visible) {
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
        
        List<Object> players = Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList;
        if(!players.isEmpty()) {
            Entity player = (Entity)players.get(0);
            
            List<ChunkCoordIntPair> newServerChunkLoadQueue = new ArrayList<>();
            
            if(Double.isNaN(lastSortX) || getLastSortDistanceSq(player) > 16 * 16) {
                int centerX = (int)Math.floor(player.posX / 16.0);
                int centerZ = (int)Math.floor(player.posZ / 16.0);
                
                for(int x = -renderRange; x <= renderRange; x++) {
                    for(int z = -renderRange; z <= renderRange; z++) {
                        int chunkX = centerX + x;
                        int chunkZ = centerZ + z;
                        
                        if(getLODChunk(chunkX, chunkZ).chunk == null) {
                            newServerChunkLoadQueue.add(new ChunkCoordIntPair(chunkX, chunkZ));
                        }
                    }
                }
                Collections.sort(newServerChunkLoadQueue, new ChunkCoordDistanceComparator(player.posX, player.posY, player.posZ));
                setServerChunkLoadQueue(newServerChunkLoadQueue);
                
                lastSortX = player.posX;
                lastSortY = player.posY;
                lastSortZ = player.posZ;
                for(Iterator<ChunkCoordIntPair> it = loadedRegionsMap.keySet().iterator(); it.hasNext();) {
                    ChunkCoordIntPair k = it.next();
                    LODRegion v = loadedRegionsMap.get(k);
                    
                    if(!v.tick(player)) {
                        System.out.println("unloading " + v);
                        v.destroy(getSaveDir());
                        it.remove();
                    }
                }
            }
        }
    }
    
    public int getFarPlaneDistanceMultiplier() {
        return renderRange / 12;
    }
	
	private void handleKeyboard() {
		if(Keyboard.isKeyDown(Keyboard.KEY_F) && !wasDown[Keyboard.KEY_F]) {
			renderLOD = !renderLOD;
		}
		if(Keyboard.isKeyDown(Keyboard.KEY_V) && !wasDown[Keyboard.KEY_V]) {
			renderWorld = !renderWorld;
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
	    
        int deletedNum = 0;
        int deletedRAM = 0;
        
        for(int i = 0; i < 2; i++) {
            piFirst[i].limit(0);
            piCount[i].limit(0);
            
            for(Iterator<Mesh> it = sentMeshes[i].iterator(); it.hasNext(); ) {
                Mesh mesh = it.next();
                if(!mesh.pendingGPUDelete) {
                    if(mesh.offset != nextMeshOffset) {
                        glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
                    }
                    mesh.iFirst = nextTri;
                    mesh.offset = nextMeshOffset;
                    
                    nextMeshOffset += mesh.buffer.limit();
                    nextTri += mesh.quadCount * 6;
                    
                    piFirst[i].limit(piFirst[i].limit() + 1);
                    piFirst[i].put(mesh.iFirst);
                    piCount[i].limit(piCount[i].limit() + 1);
                    piCount[i].put(mesh.iCount);
                } else {
                    mesh.iFirst = mesh.offset = -1;
                    mesh.visible = false;
                    mesh.pendingGPUDelete = false;
                    it.remove();
                    deletedNum++;
                    deletedRAM += mesh.buffer.limit();
                }
            }
        }
	    
	    System.out.println("Deleted " + deletedNum + " meshes, freeing up " + (deletedRAM / 1024 / 1024) + "MB of VRAM");
	    
	    glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

	private void render(float alpha) {
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
			FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
			glGetFloat(GL_MODELVIEW_MATRIX, modelView);
			
			FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
			glGetFloat(GL_PROJECTION_MATRIX, projBuf);
			
			IntBuffer viewportBuf = BufferUtils.createIntBuffer(16);
			glGetInteger(GL_VIEWPORT, viewportBuf);
			viewportBuf.limit(4);
			
			FloatBuffer projInvBuf = BufferUtils.createFloatBuffer(16);
			Matrix4f m = new Matrix4f();
			m.load(projBuf);
			projBuf.flip();
			m.invert();
			m.store(projInvBuf);
			projInvBuf.flip();
			
			FloatBuffer fogColorBuf = BufferUtils.createFloatBuffer(16);
			glGetFloat(GL_FOG_COLOR, fogColorBuf);
			fogColorBuf.limit(4);
			
			FloatBuffer fogStartEnd = BufferUtils.createFloatBuffer(2);
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
	        double interpX = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * (double)alpha;
	        double interpY = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * (double)alpha + rve.getEyeHeight();
	        double interpZ = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * (double)alpha;
	        
			glUniform3f(u_playerPos, (float)interpX - originX, (float)interpY - originY, (float)interpZ - originZ);
			
			glUniform1i(u_light, 1);
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
	
	public void onWorldRendererPost(WorldRenderer wr) {
	    int x = Math.floorDiv(wr.posX, 16);
	    int z = Math.floorDiv(wr.posZ, 16);
		LODChunk lodChunk = getLODChunk(x, z);
		if(areSurroundingChunksLoaded(x, z, wr.worldObj)) {
		    lodChunk.putChunkMeshes(Math.floorDiv(wr.posY, 16), ((IWorldRenderer)wr).getChunkMeshes());
		}
	}
	
	public boolean areSurroundingChunksLoaded(int x, int z, World world) {
	    for(int dx = -1; dx <= 1; dx++) {
            for(int dz = -1; dz <= 1; dz++) {
                if(!world.getChunkFromChunkCoords(x + dx, z + dz).isChunkLoaded) {
                    return false;
                }
            }
        }
	    return true;
	}
	
	public void onWorldRendererRender(WorldRenderer wr) {
        LODChunk lodChunk = getLODChunk(Math.floorDiv(wr.posX, 16), Math.floorDiv(wr.posZ, 16));
        int y = Math.floorDiv(wr.posY, 16);
        setMeshVisible(lodChunk.chunkMeshes[y * 2 + 0], false);
        setMeshVisible(lodChunk.chunkMeshes[y * 2 + 1], false);
        setMeshVisible(lodChunk.simpleMeshes[0], false);
        setMeshVisible(lodChunk.simpleMeshes[1], false);
	}
	
	public void onDontDraw(WorldRenderer wr) {
		int chunkX = Math.floorDiv(wr.posX, 16);
		int chunkY = Math.floorDiv(wr.posY, 16);
		int chunkZ = Math.floorDiv(wr.posZ, 16);
		
		Entity player = (Entity)Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList.get(0);
		LODChunk lodChunk = getLODChunk(chunkX, chunkZ);
	
		int y = Math.floorDiv(wr.posY, 16);
        setMeshVisible(lodChunk.chunkMeshes[y * 2 + 0], true);
        setMeshVisible(lodChunk.chunkMeshes[y * 2 + 1], true);
	}
	
	private double getLastSortDistanceSq(Entity player) {
		return Math.pow(lastSortX - player.posX, 2) + Math.pow(lastSortZ - player.posZ, 2);
	}
	
	private synchronized void setServerChunkLoadQueue(List<ChunkCoordIntPair> coords) {
		serverChunkLoadQueue = coords;
	}
	
	private LODChunk receiveFarChunk(Chunk chunk) {
		LODRegion region = getRegionContaining(chunk.xPosition, chunk.zPosition);
		myChunks.add(chunk);
		return region.putChunk(chunk);
	}
	
	private LODChunk getLODChunk(int chunkX, int chunkZ) {
		return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
	}
	
	public void onStopServer() {
	    
	}
	
	public synchronized void serverTick() {
		int chunkLoadsRemaining = 64;
		while(!serverChunkLoadQueue.isEmpty() && chunkLoadsRemaining-- > 0) {
			ChunkCoordIntPair coords = serverChunkLoadQueue.remove(0);
			ChunkProviderServer chunkProviderServer = Minecraft.getMinecraft().getIntegratedServer().worldServers[0].theChunkProviderServer;
			Chunk chunk = chunkProviderServer.currentChunkProvider.provideChunk(coords.chunkXPos, coords.chunkZPos);
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
	
	private void loadChunk(int chunkX, int chunkZ) {
		LODRegion region = getRegionContaining(chunkX, chunkZ);
		LODChunk lodChunk = region.getChunkAbsolute(chunkX, chunkZ);
		if(lodChunk == null) {
			ChunkProviderServer chunkProviderServer = Minecraft.getMinecraft().getIntegratedServer().worldServers[0].theChunkProviderServer;
			//Chunk chunk = chunkProviderServer.loadChunk(chunkX, chunkZ);
			Chunk chunk = chunkProviderServer.currentChunkProvider.provideChunk(chunkX, chunkZ);
			/*Chunk chunk = chunkProviderServer.safeLoadChunk(chunkX, chunkZ);
			if(chunk == null) {
				chunk = chunkProviderServer.currentChunkProvider.provideChunk(chunkX, chunkZ);
			}
			if(chunk != null) {
				chunk.populateChunk(chunkProviderServer, chunkProviderServer, chunkX, chunkZ);
				myChunks.add(chunk);
			}*/
			if(chunk != null) {
				myChunks.add(chunk);
			}
			//lodChunk = region.putChunk(new LODChunk(chunk));
		}
		sendChunkToGPU(lodChunk);
	}
	
	private void sendChunkToGPU(LODChunk lodChunk) {
		lodChunk.putSimpleMeshes(SimpleChunkMesh.generateSimpleMeshes(lodChunk.chunk));
		
		Entity player = (Entity) Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList.get(0);
		
		lodChunk.tick(player);
		setVisible(lodChunk, true, true);
	}
	
	public void setLOD(LODChunk lodChunk, int lod) {
		if(lod == lodChunk.lod) return;
		
		lodChunk.lod = lod;
		lodChunkChanged(lodChunk);
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
		int newLOD = (!lodChunk.hasChunkMeshes() && lodChunk.lod == 2) ? 1 : lodChunk.lod;
		for(SimpleChunkMesh sm : lodChunk.simpleMeshes) {
		    if(sm != null) {
		        if(lodChunk.visible && newLOD == 1) {
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
		for(ChunkMesh cm : lodChunk.chunkMeshes) {
			if(cm != null) {
				if(lodChunk.visible && newLOD == 2) {
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
	
	private int nextTri;
	private int nextMeshOffset;
	private int nextMesh;
	
	protected void setMeshVisible(Mesh mesh, boolean visible) {
	    setMeshVisible(mesh, visible, false);
	}
	
	protected void setMeshVisible(Mesh mesh, boolean visible, boolean force) {
		if((!force && freezeMeshes) || mesh == null) return;
		
		if(mesh.visible != visible) {
			if(!visible) {
				deleteMeshFromGPU(mesh);
			} else if(visible) {
			    sendMeshToGPU(mesh);
			}
			mesh.visible = visible;
		}
	}
	
	private void sendMeshToGPU(Mesh mesh) {
        if(mesh == null) {
            return;
        }
        if(mesh.pendingGPUDelete) {
            mesh.pendingGPUDelete = false;
            return;
        }
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
	
    private void deleteMeshFromGPU(Mesh mesh) {
        if(mesh == null) {
            return;
        }
        mesh.pendingGPUDelete = true;
    }	
	
	public Chunk getChunkFromChunkCoords(int x, int z) {
		for(Chunk chunk : myChunks) {
			if(chunk.xPosition == x && chunk.zPosition == z) {
				return chunk;
			}
		}
		return null;
	}
	
	public List<String> getDebugText() {
	    return Arrays.asList(
	            "VRAM: " + (nextMeshOffset / 1024 / 1024) + "MB / " + (BUFFER_SIZE / 1024 / 1024) + "MB",
	            "Simple meshes: " + SimpleChunkMesh.instances + " (" + SimpleChunkMesh.usedRAM / 1024 / 1024 + "MB)",
	            "Full meshes: " + ChunkMesh.instances + " (" + ChunkMesh.usedRAM / 1024 / 1024 + "MB)",
	            "Total RAM used: " + ((SimpleChunkMesh.usedRAM + ChunkMesh.usedRAM) / 1024 / 1024) + " MB"
	    );
	}
	
	public void onSave() {
	    loadedRegionsMap.forEach((k, v) -> v.save(getSaveDir()));
	}
	
	private Path getSaveDir(){
	    return Minecraft.getMinecraft().mcDataDir.toPath().resolve("lodmod").resolve(Minecraft.getMinecraft().getIntegratedServer().getFolderName());
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
