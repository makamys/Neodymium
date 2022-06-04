package makamys.lodmod.renderer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import makamys.lodmod.LODMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants.NBT;

public class LODRegion {
	
	private LODChunk[][] data = new LODChunk[32][32];
	
	int regionX, regionZ;
	
	public LODRegion(int regionX, int regionZ) {
		this.regionX = regionX;
		this.regionZ = regionZ;
		
		for(int i = 0; i < 32; i++) {
			for(int j = 0; j < 32; j++) {
				data[i][j] = new LODChunk(regionX * 32 + i, regionZ * 32 + j);
			}
		}
	}
	
	public LODRegion(int regionX, int regionZ, NBTTagCompound nbt) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        
        NBTTagList list = nbt.getTagList("chunks", NBT.TAG_COMPOUND);
        List<String> stringTable = Arrays.asList(nbt.getString("stringTable").split("\\n"));
        
        int idx = 0;
        for(int i = 0; i < 32; i++) {
            for(int j = 0; j < 32; j++) {
                data[i][j] = new LODChunk(list.getCompoundTagAt(idx++), stringTable);
                if(data[i][j].hasChunkMeshes()) {
                    LODMod.renderer.setVisible(data[i][j], true);
                }
            }
        }        
    }
	
	public static LODRegion load(Path saveDir, int regionX, int regionZ) {
	    if(!LODMod.disableChunkMeshes && LODMod.saveMeshes) {
    	    File saveFile = getSavePath(saveDir, regionX, regionZ).toFile();
    	    if(saveFile.exists()) {
    	        try {
                    NBTTagCompound nbt = CompressedStreamTools.readCompressed(new FileInputStream(saveFile));
                    return new LODRegion(regionX, regionZ, nbt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
    	    }
	    }
	    return new LODRegion(regionX, regionZ);
	}
	
	private static Path getSavePath(Path saveDir, int regionX, int regionZ) {
	    return saveDir.resolve("lod").resolve(regionX + "," + regionZ + ".lod");
	}
	
	public void save(Path saveDir) {
	    if(LODMod.disableChunkMeshes && LODMod.saveMeshes) return;
	    
	    try {
	        File saveFile = getSavePath(saveDir, regionX, regionZ).toFile();
	        saveFile.getParentFile().mkdirs();
	        
	        NBTTagCompound oldNbt = null;
	        NBTTagList oldList = null;
	        List<String> oldStringTable = null;
	        if(saveFile.exists()) {
	           oldNbt = CompressedStreamTools.readCompressed(new FileInputStream(saveFile));
	           oldList = oldNbt.getTagList("chunks", NBT.TAG_COMPOUND);;
	           oldStringTable = Arrays.asList(oldNbt.getString("stringTable").split("\\n"));
	        }
	        
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setByte("V", (byte)0);
            nbt.setString("stringTable", String.join("\n", (List<String>) ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites.keySet().stream().collect(Collectors.toList())));
            
            NBTTagList list = new NBTTagList();
            
            int idx = 0;
            for(int i = 0; i < 32; i++) {
                for(int j = 0; j < 32; j++) {
                    list.appendTag(data[i][j].saveToNBT(oldNbt == null ? null : oldList.getCompoundTagAt(idx++),
                            oldNbt == null? null : oldStringTable));
                }
            }
            nbt.setTag("chunks", list);
            
            new Thread(
                new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            CompressedStreamTools.writeCompressed(nbt, new FileOutputStream(saveFile));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            }).start();
            
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	}
	
	public LODChunk getChunkAbsolute(int chunkXAbs, int chunkZAbs) {
		return getChunk(chunkXAbs - regionX * 32, chunkZAbs - regionZ * 32);
	}
	
	public LODChunk getChunk(int x, int z) {
		if(x >= 0 && x < 32 && z >= 0 && z < 32) {
			return data[x][z];
		} else {
			return null;
		}
	}
	
	public LODChunk putChunk(Chunk chunk) {
		int relX = chunk.xPosition - regionX * 32;
		int relZ = chunk.zPosition - regionZ * 32;
		
		if(relX >= 0 && relX < 32 && relZ >= 0 && relZ < 32) {
			data[relX][relZ].receiveChunk(chunk);
			return data[relX][relZ];
		}
		return null;
	}
	
	public boolean tick(Entity player) {
	    int visibleChunks = 0;
		for(int i = 0; i < 32; i++) {
			for(int j = 0; j < 32; j++) {
				LODChunk chunk = data[i][j];
				if(chunk != null) {
					chunk.tick(player);
					if(chunk.visible) {
					    visibleChunks++;
					}
				}
			}
		}
		return visibleChunks > 0;
	}
	
	public void destroy(Path saveDir) {
	    save(saveDir);
	    for(int i = 0; i < 32; i++) {
            for(int j = 0; j < 32; j++) {
                LODChunk chunk = data[i][j];
                if(chunk != null) {
                    chunk.destroy();
                }
            }
        }
	}
	
	public double distanceTaxicab(Entity entity) {
	    double centerX = ((regionX * 32) + 16) * 16;
	    double centerZ = ((regionZ * 32) + 16) * 16;
	    
	    return Math.max(Math.abs(centerX - entity.posX), Math.abs(centerZ - entity.posZ));
	    
	}
	
	@Override
	public String toString() {
	    return "LODRegion(" + regionX + ", " + regionZ + ")";
	}
	
}
