package makamys.lodmod.renderer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;

import makamys.lodmod.LODMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.IIcon;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import scala.actors.threadpool.Arrays;

public class SimpleChunkMesh extends Mesh {
	
	private FloatBuffer vertices;
	
	public static int usedRAM;
	public static int instances;
	
	public static List<SimpleChunkMesh> generateSimpleMeshes(Chunk target){
		int divisions = 4;
		
		SimpleChunkMesh pass1 = new SimpleChunkMesh(target.xPosition, target.zPosition, divisions * divisions * 25, 0);
		SimpleChunkMesh pass2 = new SimpleChunkMesh(target.xPosition, target.zPosition, divisions * divisions * 25, 1);
		
		for(int divX = 0; divX < divisions; divX++) {
			for(int divZ = 0; divZ < divisions; divZ++) {
				IIcon icon = null;
				int color = 0xFFFFFFFF;
				int size = 16 / divisions;
				int y = 255;
				boolean foundWater = false;
				
				int xOff = divX * size;
                int zOff = divZ * size;
				
				BiomeGenBase biome = target.getBiomeGenForWorldCoords(xOff, zOff, target.worldObj.getWorldChunkManager());
				
				for(y = 255; y > 0; y--) {
					Block block = target.getBlock(xOff, y, zOff);
					
					int worldX = target.xPosition * 16 + divX * size;
					int worldY = y;
					int worldZ = target.zPosition * 16 + divZ * size;
					
					if(!foundWater && block.getMaterial() == Material.water) {
						foundWater = true;
						int meta = target.getBlockMetadata(xOff, y, zOff);
						IIcon waterIcon = block.getIcon(1, meta);
						
						int waterColor = biome.getWaterColorMultiplier();
						waterColor |= 0xFF000000;
						pass2.addFaceYPos(worldX, worldY, worldZ, size, size, waterIcon, waterColor);
					}
					
					if(block.isBlockNormalCube() && block.isOpaqueCube() && block.renderAsNormalBlock()) {
						int meta = target.getBlockMetadata(xOff, y, zOff);
						icon = block.getIcon(1, meta);
						
						if(block instanceof BlockGrass) {
						    color = biome.getBiomeGrassColor(worldX, y, worldZ);
						} else if(block instanceof BlockLeaves) {
						    color = biome.getBiomeFoliageColor(worldX, y, worldZ);
						} else {
						    color = block.colorMultiplier(target.worldObj, worldX, y, worldZ);
						}
						color = (0xFF << 24) | ((color >> 16 & 0xFF) << 0) | ((color >> 8 & 0xFF) << 8) | ((color >> 0 & 0xFF) << 16);
						
						pass1.addCube(worldX, worldY, worldZ, size, size, worldY, icon, color);
						break;
					}
				}
			}
		}
		
		pass1.finish();
		pass2.finish();
		
		return Arrays.asList(new SimpleChunkMesh[] {pass1.quadCount != 0 ? pass1 : null, pass2.quadCount != 0 ? pass2 : null});
	}
	
	public SimpleChunkMesh(int x, int z, int maxQuads, int pass) {
	    this.x = x;
	    this.y = 64;
	    this.z = z;
	    this.pass = pass;
	    
	    buffer = BufferUtils.createByteBuffer(4 * 6 * 7 * maxQuads);
        vertices = buffer.asFloatBuffer();
	}
	
	public void finish() {
	    vertices.flip();
	    buffer.limit(vertices.limit() * 4);
	    
	    // may want to shrink the buffers to actual size to not waste memory
	    
	    usedRAM += buffer.limit();
        instances++;
	}
	
	private void addCube(float x, float y, float z, float sizeX, float sizeZ, float sizeY, IIcon icon, int color) {
		addFaceYPos(x, y, z, sizeX, sizeZ, icon, color);
		addFace(
			x + 0, y - sizeY, z + 0,
			x + 0, y + 0, z + 0,
			x + sizeX, y + 0, z + 0,
			x + sizeX, y - sizeY, z + 0,
			icon, color, 180
		);
		addFace(
			x + sizeX, y - sizeY, z + sizeZ,
			x + sizeX, y + 0, z + sizeZ,
			x + 0, y + 0, z + sizeZ,
			x + 0, y - sizeY, z + sizeZ,
			icon, color, 180
		);
		addFace(
				x + sizeX, y - sizeY, z + 0,
				x + sizeX, y + 0, z + 0,
				x + sizeX, y + 0, z + sizeZ,
				x + sizeX, y - sizeY, z + sizeZ,
				icon, color, 120
			);
		addFace(
				x + 0, y - sizeY, z + sizeZ,
				x + 0, y + 0, z + sizeZ,
				x + 0, y + 0, z + 0,
				x + 0, y - sizeY, z + 0,
				icon, color, 120
			);
	}
	
	private void addFaceYPos(float x, float y, float z, float sizeX, float sizeZ, IIcon icon, int color) {
	    addFace(
                x + 0, y + 0, z + 0,
                x + 0, y + 0, z + sizeZ,
                x + sizeX, y + 0, z + sizeZ,
                x + sizeX, y + 0, z + 0,
                icon, color, 240
                );
	}
	
	private void addFace(float p1x, float p1y, float p1z,
			float p2x, float p2y, float p2z,
			float p3x, float p3y, float p3z,
			float p4x, float p4y, float p4z,
			IIcon icon, int color, int brightness) {
		int off = vertices.position() * 4;
		vertices.put(new float[] {
				p1x, p1y, p1z, icon.getMinU(), icon.getMaxV(), 0, 0,
				p2x, p2y, p2z, icon.getMinU(), icon.getMinV(), 0, 0,
				p4x, p4y, p4z, icon.getMaxU(), icon.getMaxV(), 0, 0,
				p2x, p2y, p2z, icon.getMinU(), icon.getMinV(), 0, 0,
				p3x, p3y, p3z, icon.getMaxU(), icon.getMinV(), 0, 0,
				p4x, p4y, p4z, icon.getMaxU(), icon.getMaxV(), 0, 0
				});
		buffer.putInt(off + 0 * getStride() + 6 * 4, color);
		buffer.putShort(off + 0 * getStride() + 5 * 4 + 2, (short)brightness);
		buffer.putInt(off + 1 * getStride() + 6 * 4, color);
		buffer.putShort(off + 1 * getStride() + 5 * 4 + 2, (short)brightness);
		buffer.putInt(off + 2 * getStride() + 6 * 4, color);
		buffer.putShort(off + 2 * getStride() + 5 * 4 + 2, (short)brightness);
		buffer.putInt(off + 3 * getStride() + 6 * 4, color);
		buffer.putShort(off + 3 * getStride() + 5 * 4 + 2, (short)brightness);
		buffer.putInt(off + 4 * getStride() + 6 * 4, color);
		buffer.putShort(off + 4 * getStride() + 5 * 4 + 2, (short)brightness);
		buffer.putInt(off + 5 * getStride() + 6 * 4, color);
		buffer.putShort(off + 5 * getStride() + 5 * 4 + 2, (short)brightness);
		
		quadCount++;
	}
	
	public int getStride() {
		return (3 * 4 + 8 + 4 + 4);
	}
	
	public void destroy() {
        usedRAM -= buffer.limit();
        instances--;
	}
	
}
