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
import java.util.Arrays;
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
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.IIcon;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

public class SimpleChunkMesh extends Mesh {
	
	private FloatBuffer vertices;
	
	public static int usedRAM;
	public static int instances;
	public static int divisions = 4;
	
	private static boolean isSolid(Block block) {
	    return block.isBlockNormalCube() && block.isOpaqueCube() && block.renderAsNormalBlock();
	}
	
	private static boolean isBad(Block block) {
	    for(Class clazz : LODMod.blockClassBlacklist) {
	        if(clazz.isInstance(block)) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static List<SimpleChunkMesh> generateSimpleMeshes(Chunk target){
		SimpleChunkMesh pass1 = new SimpleChunkMesh(target.xPosition, target.zPosition, divisions * divisions * 25, 0);
		SimpleChunkMesh pass2 = new SimpleChunkMesh(target.xPosition, target.zPosition, divisions * divisions * 25, 1);
		
		SimpleChunkMeshBuilder builder = new SimpleChunkMeshBuilder();
		
		for(int divX = 0; divX < divisions; divX++) {
			for(int divZ = 0; divZ < divisions; divZ++) {
				IIcon icon = null;
				int color = 0xFFFFFFFF;
				int size = 16 / divisions;
				int y = 255;
				boolean foundWater = false;
				
				int xOff = divX * size;
                int zOff = divZ * size;
				
                int biomeId = target.getBiomeArray()[xOff << 4 | zOff] & 255;
                if(biomeId == 255) {
                    System.out.println("Missing biome data for chunk " + target.xPosition + ", " + target.zPosition);
                }
                BiomeGenBase biome = BiomeGenBase.getBiome(biomeId) == null ? BiomeGenBase.plains : BiomeGenBase.getBiome(biomeId);
				
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
						pass2.addFaceYPos(worldX, worldY, worldZ, size, size, waterIcon, waterColor, 1);
					}
					
					if(isSolid(block) && isBad(block)) {
                        for(int dx = -1; dx <= 1; dx++) {
                            for(int dz = -1; dz <= 1; dz++) {
                                int newX = xOff + dx;
                                int newZ = zOff + dz;
                                if(newX >= 0 && newX < 16 && newZ >= 0 && newZ < 16) {
                                    Block newBlock = target.getBlock(newX, y, newZ);
                                    if(!isBad(newBlock)) {
                                        xOff += dx;
                                        zOff += dz;
                                        worldX += dx;
                                        worldZ += dz;
                                        block = newBlock;
                                    }
                                }
                            }
                        }
                    }
					if(isSolid(block)) {
					    
					    float brightnessMult = foundWater ? 0.2f : 1f;
						int meta = target.getBlockMetadata(xOff, y, zOff);
						icon = block.getIcon(1, meta);
						
						if(block instanceof BlockGrass) {
						    color = biome.getBiomeGrassColor(worldX, y, worldZ);
						} else if(block instanceof BlockLeaves) {
						    color = biome.getBiomeFoliageColor(worldX, y, worldZ);
						} else {
						    color = block.colorMultiplier(Minecraft.getMinecraft().theWorld, worldX, y, worldZ);
						}
						color = (0xFF << 24) | ((color >> 16 & 0xFF) << 0) | ((color >> 8 & 0xFF) << 8) | ((color >> 0 & 0xFF) << 16);
						
						if(biome.getFloatTemperature(worldX, y, worldZ) < 0.15f) {
						    
						    builder.addCube(divX, divZ, worldY + 0.2f, 1f, Blocks.snow_layer.getIcon(1, 0), 0xFFFFFFFF, brightnessMult);
						    builder.addCube(divX, divZ, worldY - 0.8f, -1, icon, color, brightnessMult);
						} else {
						    builder.addCube(divX, divZ, worldY, -1, icon, color, brightnessMult);
						}
						
						
						break;
					}
				}
			}
		}
		
		builder.render(pass1, target.xPosition, target.zPosition);
		
		pass1.finish();
		pass2.finish();
		
		return Arrays.asList(new SimpleChunkMesh[] {pass1.quadCount != 0 ? pass1 : null, pass2.quadCount != 0 ? pass2 : null});
	}
	
	private static class SimpleChunkMeshBuilder {
	    int maxIconsPerColumn = 2;
	    float[][][] heights = new float[divisions][divisions][maxIconsPerColumn];
        float[][][] depths = new float[divisions][divisions][maxIconsPerColumn];
        IIcon[][][] icons = new IIcon[divisions][divisions][maxIconsPerColumn];
        int[][][] colors = new int[divisions][divisions][maxIconsPerColumn];
        float[][][] brightnessMults = new float[divisions][divisions][maxIconsPerColumn];
        
        public void addCube(int x, int z, float height, float depth, IIcon icon, int color, float brightnessMult) {
            IIcon[] iconz = icons[x][z];
            int i = iconz[0] == null ? 0 : 1;
            if(iconz[0] != null && iconz[1] != null) {
                throw new IllegalStateException("Too many icons in column");
            }
            
            heights[x][z][i] = height;
            depths[x][z][i] = depth;
            icons[x][z][i] = icon;
            colors[x][z][i] = color;
            brightnessMults[x][z][i] = brightnessMult;
        }
        
        public void render(SimpleChunkMesh mesh, int chunkX, int chunkZ) {
            float size = 16 / divisions;
            
            for(int x = 0; x < divisions; x++) {
                for(int z = 0; z < divisions; z++) {
                    float worldX = chunkX * 16 + x * size;
                    float worldZ = chunkZ * 16 + z * size;
                    for(int i = 0; i < maxIconsPerColumn; i++) {
                        IIcon icon = icons[x][z][i];
                        if(icon != null) {
                            float height = heights[x][z][i];
                            float depthValue = depths[x][z][i];
                            float depth = depthValue == -1 ? height : depthValue;
                            int color = colors[x][z][i];
                            float brightnessMult = brightnessMults[x][z][i];
                            
                            if(i == 0) {
                                mesh.addFaceYPos(worldX, height, worldZ, size, size, icon, color, brightnessMult);
                            }
                            float heightX0 = x > 0 ? heights[x - 1][z][0] : 0;
                            if(heightX0 < height) {
                                mesh.addFaceX2(worldX, height, worldZ, Math.min(depth, height - heightX0), size, icon, color, brightnessMult);
                            }
                            
                            float heightX1 = x < divisions - 1 ? heights[x + 1][z][0] : 0;
                            if(heightX1 < height) {
                                mesh.addFaceX1(worldX + size, height, worldZ, Math.min(depth, height - heightX1), size, icon, color, brightnessMult);
                            }
                        
                            float heightZ0 = z > 0 ? heights[x][z - 1][0] : 0;
                            if(heightZ0 < height) {
                                mesh.addFaceZ1(worldX, height, worldZ, size, Math.min(depth, height - heightZ0), icon, color, brightnessMult);
                            }
                            
                            float heightZ1 = z < divisions - 1 ? heights[x][z + 1][0] : 0;
                            if(heightZ1 < height) {
                                mesh.addFaceZ2(worldX, height, worldZ + size, size, Math.min(depth, height - heightZ1), icon, color, brightnessMult);
                            }
                        }
                    }
                }
            }
        }
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
	
	private void addCube(float x, float y, float z, float sizeX, float sizeZ, float sizeY, IIcon icon, int color, float brightnessMult) {
		addFaceYPos(x, y, z, sizeX, sizeZ, icon, color, brightnessMult);
		addFaceZ1(x, y, z, sizeX, sizeY, icon, color, brightnessMult);
		addFaceZ2(x, y, z + sizeZ, sizeX, sizeY, icon, color, brightnessMult);
		addFaceX1(x + sizeX, y, z, sizeX, sizeY, icon, color, brightnessMult);
        addFaceX2(x, y, z, sizeX, sizeY, icon, color, brightnessMult);
	}
	
	private void addFaceZ1(float x, float y, float z, float sizeX, float sizeY, IIcon icon, int color, float brightnessMult) {
	    addFace(
	            x + 0, y - sizeY, z + 0,
	            x + 0, y + 0, z + 0,
	            x + sizeX, y + 0, z + 0,
	            x + sizeX, y - sizeY, z + 0,
	            icon, color, (int)(200 * brightnessMult) 
	        );
	}
	
	private void addFaceZ2(float x, float y, float z, float sizeX, float sizeY, IIcon icon, int color, float brightnessMult) {
	    addFace(
	            x + sizeX, y - sizeY, z,
	            x + sizeX, y + 0, z,
	            x + 0, y + 0, z,
	            x + 0, y - sizeY, z,
	            icon, color, (int)(200 * brightnessMult)
	        );
    }
	
   private void addFaceX1(float x, float y, float z, float sizeY, float sizeZ, IIcon icon, int color, float brightnessMult) {
        addFace(
                x, y - sizeY, z + 0,
                x, y + 0, z + 0,
                x, y + 0, z + sizeZ,
                x, y - sizeY, z + sizeZ,
                icon, color, (int)(160 * brightnessMult)
            );
    }
    
    private void addFaceX2(float x, float y, float z, float sizeY, float sizeZ, IIcon icon, int color, float brightnessMult) {
        addFace(
                x + 0, y - sizeY, z + sizeZ,
                x + 0, y + 0, z + sizeZ,
                x + 0, y + 0, z + 0,
                x + 0, y - sizeY, z + 0,
                icon, color, (int)(160 * brightnessMult)
            );
    }
	
	private void addFaceYPos(float x, float y, float z, float sizeX, float sizeZ, IIcon icon, int color, float brightnessMult) {
	    addFace(
                x + 0, y + 0, z + 0,
                x + 0, y + 0, z + sizeZ,
                x + sizeX, y + 0, z + sizeZ,
                x + sizeX, y + 0, z + 0,
                icon, color, (int)(240 * brightnessMult)
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
	
	public static void prepareFarChunkOnServer(Chunk chunk) {
	    for(int divX = 0; divX < divisions; divX++) {
            for(int divZ = 0; divZ < divisions; divZ++) {
                int size = 16 / divisions;
                
                int xOff = divX * size;
                int zOff = divZ * size;
                
                chunk.getBiomeGenForWorldCoords(xOff, zOff, chunk.worldObj.getWorldChunkManager());
            }
	    }
	}
	
}
