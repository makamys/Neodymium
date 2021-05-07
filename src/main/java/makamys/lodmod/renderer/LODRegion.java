package makamys.lodmod.renderer;

import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;

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
	
	public static LODRegion load(int regionX, int regionZ) {
		return new LODRegion(regionX, regionZ); // TODO
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
			data[relX][relZ].chunk = chunk;
			data[relX][relZ].waitingForData = false;
			return data[relX][relZ];
		}
		return null;
	}
	
	public void tick(Entity player) {
		for(int i = 0; i < 32; i++) {
			for(int j = 0; j < 32; j++) {
				LODChunk chunk = data[i][j];
				if(chunk != null) {
					chunk.tick(player);
				}
			}
		}
	}
	
}
