package makamys.lodmod.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ARBDebugOutputCallback;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.vector.Matrix4f;

import makamys.lodmod.ducks.IWorldRenderer;
import makamys.lodmod.util.Util;

import static org.lwjgl.opengl.ARBBufferObject.*;
import static org.lwjgl.opengl.ARBVertexBufferObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.util.glu.GLU.*;

public class MyRenderer {

	private static boolean hasInited = false;
	
	private static boolean[] wasDown = new boolean[256];
	private static int renderQuads = 0;
	
	public static boolean renderWorld = true;
	public static boolean renderLOD = true;
	
	//private static int[] spriteIndexMap;
	public static List<TextureAtlasSprite> sprites;
	
	static private Map<Long, Integer> uv2spriteIndex = new HashMap<>();
	
	//static ChunkMesh mesh;
	
	private static void destroy() {
		glDeleteProgram(shaderProgram);
		glDeleteVertexArrays(VAO);
		glDeleteBuffers(VBO);
	}
	
	private static void mainLoop() {
		if(Keyboard.isKeyDown(Keyboard.KEY_F) && !wasDown[Keyboard.KEY_F]) {
			renderLOD = !renderLOD;
		}
		if(Keyboard.isKeyDown(Keyboard.KEY_V) && !wasDown[Keyboard.KEY_V]) {
			renderWorld = !renderWorld;
		}
		if(hasInited) {
			/*if(Keyboard.isKeyDown(Keyboard.KEY_X) && !wasDown[Keyboard.KEY_X]) {
				renderQuads--;
				if(renderQuads < 0) {
					renderQuads = 0;
				}
			}
			if(Keyboard.isKeyDown(Keyboard.KEY_C) && !wasDown[Keyboard.KEY_C]) {
				renderQuads++;
				if(renderQuads > mesh.quadCount) {
					renderQuads = mesh.quadCount;
				}
			}*/
		}
		
		
		for(int i = 0; i < 256; i++) {
			wasDown[i] = Keyboard.isKeyDown(i);
		}
	}

	private static void render() {
	    GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
	    //GL11.glDisable(GL11.GL_CULL_FACE);
	    //GL11.glDisable(GL11.GL_LIGHTING);
	    GL11.glDisable(GL11.GL_TEXTURE_2D);

	    GL11.glEnable(GL11.GL_BLEND);
	    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	    //GL11.glColor4f(0, 1, 0, 1);   // change this for your colour
	    //GL11.glLineWidth(9.0F);
	    //GL11.glDepthMask(false);
	    float z = 0;
	    
	    /*GL11.glBegin(GL11.GL_TRIANGLES);
	    GL11.glVertex3f(-0.9f,-0.9f,0);
	    GL11.glVertex3f(0.9f,0.9f,0);
	    GL11.glVertex3f(-0.9f,0.9f,0);
	    GL11.glEnd();*/
	    
	    //glClear(GL_COLOR_BUFFER_BIT);
	    
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
			//modelView.flip();
			
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
			//glUniform4(u_viewport, viewportBuf);
			glUniform4f(u_viewport, viewportBuf.get(0),viewportBuf.get(1),viewportBuf.get(2),viewportBuf.get(3));
			glUniform4(u_fogColor, fogColorBuf);
			glUniform2(u_fogStartEnd, fogStartEnd);
			
			float originX = 0;
			float originY = 0;
			float originZ = 0;
			
			glUniform3f(u_playerPos, (float)EntityFX.interpPosX - originX, (float)EntityFX.interpPosY - originY, (float)EntityFX.interpPosZ - originZ);
			
			glUniform1i(u_light, 1);
		}
		
	    glBindVertexArray(VAO);
	    //glEnableVertexAttribArray(0);
	    //glDrawArrays(GL_TRIANGLES, 0, 3);
	    //glActiveTexture(GL_TEXTURE1);
	    //ITextureObject hmm = Minecraft.getMinecraft().getTextureManager().getTexture(new ResourceLocation("dynamic/lightMap_1"));
	    //glBindTexture(GL_TEXTURE_2D, hmm.getGlTextureId());
	    //glDrawArrays(GL_TRIANGLES, 0, 6*renderQuads);
	    glMultiDrawArrays(GL_TRIANGLES, piFirst, piCount);
	    
	    //glDisableVertexAttribArray(0);
	    glBindVertexArray(0);
	    glUseProgram(0);
	    
	    GL11.glDepthMask(true);
	    GL11.glPopAttrib();
	    
	    
	}
	
	private static int VAO, VBO, EBO, shaderProgram;
	private static IntBuffer piFirst, piCount;
	
	private static String readFile(String path){
		try {
			return new String(Files.readAllBytes(Util.getResourcePath(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	private static byte[] byteBufferToArray(ByteBuffer buffer) {
		byte[] dst = new byte[buffer.remaining()];
		buffer.get(dst);
		buffer.flip();
		return dst;
	}
	
	private static int[] intBufferToArray(IntBuffer buffer) {
		int[] dst = new int[buffer.remaining()];
		buffer.get(dst);
		buffer.flip();
		return dst;
	}
	
	private static int findSpriteIndexForUV(float u, float v) {
		Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
		
		int spriteIndex = 0;
		for(TextureAtlasSprite tas : uploadedSprites.values()) {
    		if(tas.getMinU() <= u && u <= tas.getMaxU() && tas.getMinV() <= v && v <= tas.getMaxV()) {
    			break;
    		}
    		spriteIndex++;
    	}
		return spriteIndex;
	}
	
	public static int getSpriteIndexForUV(float u, float v){
		long key = ChunkCoordIntPair.chunkXZ2Int((int)(u * Integer.MAX_VALUE), (int)(v * Integer.MAX_VALUE));
		int index = uv2spriteIndex.getOrDefault(key, -1);
		if(index == -1) {
			index = findSpriteIndexForUV(u, v);
			uv2spriteIndex.put(key, index);
		}
		return index;
	}
	
	public static TextureAtlasSprite getSprite(int i){
		if(i >= 0 && i < sprites.size()) {
			return sprites.get(i);
		} else {
			return null;
		}
	}
	
	private static boolean init() {
		Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
		sprites = uploadedSprites.values().stream().collect(Collectors.toList());
		
		int vertexShader;
		vertexShader = glCreateShader(GL_VERTEX_SHADER);
		
		glShaderSource(vertexShader, readFile("shaders/chunk.vert"));
		glCompileShader(vertexShader);
		
		if(glGetShaderi(vertexShader, GL_COMPILE_STATUS) == 0) {
			System.out.println("Error compiling vertex shader: " + glGetShaderInfoLog(vertexShader, 256));
		}
		
		int fragmentShader;
		fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
		
		glShaderSource(fragmentShader, readFile("shaders/chunk.frag"));
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
		
		EBO = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO);
		boolean rough = false;
		
		/*
		List<Integer> chunkCoords = new ArrayList<>();*/
		/*chunkCoords.add(-19);
		chunkCoords.add(4);
		chunkCoords.add(15);*/
		/*List<Mesh> meshes = new ArrayList<Mesh>();
		for(Chunk chunk : myChunks) {
			Entity player = Minecraft.getMinecraft().thePlayer;
			int minRadius = 0;
			int radius = 10;
			if(minRadius <= Math.abs(chunk.xPosition - player.chunkCoordX) && Math.abs(chunk.xPosition - player.chunkCoordX) < radius && minRadius <= Math.abs(chunk.zPosition - player.chunkCoordZ) && Math.abs(chunk.zPosition - player.chunkCoordZ) < radius) {
				if(!rough) {
					for(int y = 0; y < 16; y++) {
						chunkCoords.add(chunk.xPosition);
						chunkCoords.add(y);
						chunkCoords.add(chunk.zPosition);
					}
				} else {
					meshes.add(new SimpleChunkMesh(chunk));
				}
			}
			
		}
		long t0 = System.nanoTime();
		if(!rough) {
			ChunkMesh.saveChunks(chunkCoords);
			
			meshes.addAll(ChunkMesh.loadAll());
		}
		System.out.println("loaded " + meshes.size() + " cchunks in " + ((System.nanoTime() - t0) / 1000000000f) + "s");
		//meshes = meshes.subList(2, 3);
		int bufferSize = 0;
		int quadCount = 0;
		Mesh firstMesh = meshes.get(0);
		int stride = -1;
		for(Mesh mesh : meshes) {
			bufferSize += mesh.buffer.limit();
			quadCount += mesh.quadCount;
		}
		
		if(Runtime.getRuntime().freeMemory() < BUFFER_SIZE) {
			System.out.println("not enough memory");
			// TODO optimize memory usage
			return false;
		}
		ByteBuffer buffer = BufferUtils.createByteBuffer(BUFFER_SIZE);
		
		for(Mesh mesh : meshes) {
			int verticesSoFar = buffer.position() / firstMesh.getStride();
			buffer.put(mesh.buffer);
		}
		buffer.flip();*/
		
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
		
		//renderQuads = quadCount;
		
		int nextTri = 0;
		piFirst = BufferUtils.createIntBuffer(MAX_MESHES);
		piFirst.flip();
		piCount = BufferUtils.createIntBuffer(MAX_MESHES);
		piCount.flip();
		/*for(int i = 0; i < meshes.size(); i++) {
			Mesh mesh = meshes.get(i);
			
			piFirst.put(nextTri);
			piCount.put(mesh.quadCount * 6);
			nextTri += mesh.quadCount * 6;
		}*/
		
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
		//glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		
		return true;
	}
	
	public static boolean doRender = true;
	private static boolean hasInited2 = false;
	
	private static int BUFFER_SIZE = 1024 * 1024 * 1024;
	private static int MAX_MESHES = 100000;
	
	public static void beforeRenderTerrain() {
		init2();
		
		if(doRender) {
			mainLoop();
			if(hasInited && renderLOD) {
				render();
			}
		}
	}
	
	public static void onWorldRendererPost(WorldRenderer wr) {
		LODChunk lodChunk = getLODChunk(Math.floorDiv(wr.posX, 16), Math.floorDiv(wr.posZ, 16));
		lodChunk.putChunkMeshes(Math.floorDiv(wr.posY, 16), ((IWorldRenderer)wr).getChunkMeshes());
		setVisible(lodChunk, false);
	}
	
	public static void onDontDraw(WorldRenderer wr) {
		int chunkX = Math.floorDiv(wr.posX, 16);
		int chunkY = Math.floorDiv(wr.posY, 16);
		int chunkZ = Math.floorDiv(wr.posZ, 16);
		
		Entity player = (Entity)Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList.get(0);
		LODChunk lodChunk = getLODChunk(chunkX, chunkZ);
		
		if(lodChunk.hasChunkMeshes()) {
			setLOD(lodChunk, 2);//(lodChunk.distSq(player) < (16 * 16 * 16 * 16)) ? 1 : 2);
		}
		setVisible(lodChunk, true);
	}
	
	private static void init2() {
		if(!hasInited2) {
			hasInited = init();
			hasInited2 = true;
		} else {
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
					
					int range = 64;
					for(int x = -range; x <= range; x++) {
						for(int z = -range; z <= range; z++) {
							int chunkX = centerX + x;
							int chunkZ = centerZ + z;
							
							if(getLODChunk(chunkX, chunkZ).chunk == null) {
								newServerChunkLoadQueue.add(new ChunkCoordIntPair(chunkX, chunkZ));
							}
						}
					}
					Collections.sort(newServerChunkLoadQueue, new ChunkCoordDistanceComparator(player));
					setServerChunkLoadQueue(newServerChunkLoadQueue);
					hasInited2 = true;
					
					lastSortX = player.posX;
					lastSortY = player.posY;
					lastSortZ = player.posZ;
					
					loadedRegionsMap.forEach((k, v) -> v.tick(player));
				}
			}
		}
	}
	
	private static double getLastSortDistanceSq(Entity player) {
		return Math.pow(lastSortX - player.posX, 2) + Math.pow(lastSortZ - player.posZ, 2);
	}
	
	private static synchronized void setServerChunkLoadQueue(List<ChunkCoordIntPair> coords) {
		serverChunkLoadQueue = coords;
	}
	
	private static LODChunk receiveFarChunk(Chunk chunk) {
		LODRegion region = getRegionContaining(chunk.xPosition, chunk.zPosition);
		myChunks.add(chunk);
		return region.putChunk(chunk);
	}
	
	private static LODChunk getLODChunk(int chunkX, int chunkZ) {
		return getRegionContaining(chunkX, chunkZ).getChunkAbsolute(chunkX, chunkZ);
	}
	
	static List<Chunk> myChunks = new ArrayList<Chunk>();
	static List<LODChunk> pendingLODChunks = new ArrayList<>();
	
	private static boolean hasServerInited = false;
	private static HashMap<ChunkCoordIntPair, LODRegion> loadedRegionsMap = new HashMap<>();
	
	// TODO make these packets to make this work on dedicated servers
	static Queue<Chunk> farChunks = new ConcurrentLinkedQueue<>();
	
	static List<ChunkCoordIntPair> serverChunkLoadQueue = new ArrayList<>();
	
	private static double lastSortX = Double.NaN;
	private static double lastSortY = Double.NaN;
	private static double lastSortZ = Double.NaN;
	
	public static void onStopServer() {
	    
	}
	
	public static synchronized void serverTick() {
		int chunkLoadsRemaining = 64;
		while(!serverChunkLoadQueue.isEmpty() && chunkLoadsRemaining-- > 0) {
			ChunkCoordIntPair coords = serverChunkLoadQueue.remove(0);
			ChunkProviderServer chunkProviderServer = Minecraft.getMinecraft().getIntegratedServer().worldServers[0].theChunkProviderServer;
			Chunk chunk = chunkProviderServer.currentChunkProvider.provideChunk(coords.chunkXPos, coords.chunkZPos);
			farChunks.add(chunk);
		}
	}
	
	private static LODRegion getRegionContaining(int chunkX, int chunkZ) {
		ChunkCoordIntPair key = new ChunkCoordIntPair(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ, 32));
		LODRegion region = loadedRegionsMap.get(key);
		if(region == null) {
			region = LODRegion.load(Math.floorDiv(chunkX , 32), Math.floorDiv(chunkZ , 32));
			loadedRegionsMap.put(key, region);
		}
		return region;
	}
	
	private static void loadChunk(int chunkX, int chunkZ) {
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
	
	private static void sendChunkToGPU(LODChunk lodChunk) {
		lodChunk.simpleMesh = new SimpleChunkMesh(lodChunk.chunk);
		/*for(int cy = 0; cy < 16; cy++) {
			lodChunk.chunkMeshes[cy] = ChunkMesh.getChunkMesh(lodChunk.x, cy, lodChunk.z);
			sendMeshToGPU(lodChunk.chunkMeshes[cy]);
		}*/
		
		sendMeshToGPU(lodChunk.simpleMesh);
		
		Entity player = (Entity) Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList.get(0);
		
		setLOD(lodChunk, 1);//lodChunk.distSq(player) < 16 * 16 * 16 * 16 ? 2 : 1);
		setVisible(lodChunk, true);
	}
	
	public static void setLOD(LODChunk lodChunk, int lod) {
		if(lod == lodChunk.lod) return;
		
		lodChunk.lod = lod;
		lodChunkChanged(lodChunk);
	}
	
	public static void setVisible(LODChunk lodChunk, boolean visible) {
		if(visible == lodChunk.visible) return;
		
		lodChunk.visible = visible;
		lodChunkChanged(lodChunk);
	}
	
	public static void lodChunkChanged(LODChunk lodChunk) {
		int newLOD = (!lodChunk.hasChunkMeshes() && lodChunk.lod == 2) ? 1 : lodChunk.lod;
		if(lodChunk.simpleMesh != null) {
			if(lodChunk.visible && newLOD == 1) {
				if(!lodChunk.simpleMesh.visible) {
					setMeshVisible(lodChunk.simpleMesh, true);
				}
			} else {
				if(lodChunk.simpleMesh.visible) {
					setMeshVisible(lodChunk.simpleMesh, false);
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
	
	private static int nextTri;
	private static int nextMeshOffset;
	private static int nextMesh;
	
	protected static void sendMeshToGPU(Mesh mesh) {
		if(mesh == null) {
			return;
		}
		glBindVertexArray(VAO);
		glBindBuffer(GL_ARRAY_BUFFER, VBO);
		
		glBufferSubData(GL_ARRAY_BUFFER, nextMeshOffset, mesh.buffer);
		mesh.iFirst = nextTri;
		mesh.iCount = mesh.quadCount * 6;
		
		nextTri += mesh.quadCount * 6;
		nextMeshOffset += mesh.buffer.limit();
		
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}
	
	protected static void setMeshVisible(Mesh mesh, boolean visible) {
		if(mesh == null) return;
		
		if(mesh.visible != visible) {
			if(!visible) {
				piFirst.position(0);
				int[] piFirstArr = new int[piFirst.limit()];
				piFirst.get(piFirstArr);
				int index = ArrayUtils.indexOf(piFirstArr, mesh.iFirst);
				piFirstArr = ArrayUtils.remove(piFirstArr, index);
				piFirst.position(0);
				piFirst.put(piFirstArr);
				piFirst.position(0);
				piFirst.limit(piFirst.limit() - 1);
				
				piCount.position(0);
				int[] piCountArr = new int[piCount.limit()];
				piCount.get(piCountArr);
				piCountArr = ArrayUtils.remove(piCountArr, index);
				piCount.position(0);
				piCount.put(piCountArr);
				piCount.position(0);
				piCount.limit(piCount.limit() - 1);
				nextMesh--;
			} else if(visible) {
				piFirst.limit(piFirst.limit() + 1);
				piFirst.put(nextMesh, mesh.iFirst);
				piCount.limit(piCount.limit() + 1);
				piCount.put(nextMesh, mesh.iCount);
				nextMesh++;
			}
			mesh.visible = visible;
		}
	}
	
	public static Chunk getChunkFromChunkCoords(int x, int z) {
		for(Chunk chunk : myChunks) {
			if(chunk.xPosition == x && chunk.zPosition == z) {
				return chunk;
			}
		}
		return null;
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
		Entity player;
		
		public ChunkCoordDistanceComparator(Entity player) {
			this.player = player;
		}
		
		@Override
		public int compare(ChunkCoordIntPair p1, ChunkCoordIntPair p2) {
			int distSq1 = distSq(p1);
			int distSq2 = distSq(p2);
			return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
		}
		
		int distSq(ChunkCoordIntPair p) {
			return (int)(
					Math.pow(((p.chunkXPos * 16) - player.posX), 2) +
					Math.pow(((p.chunkZPos * 16) - player.posZ), 2)
					);
		}
	}
}
