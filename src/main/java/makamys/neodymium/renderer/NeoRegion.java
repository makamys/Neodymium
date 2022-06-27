package makamys.neodymium.renderer;

import java.nio.file.Path;

import net.minecraft.entity.Entity;

public class NeoRegion {
	
	private NeoChunk[][] data = new NeoChunk[32][32];
	
	int regionX, regionZ;
	
	public int meshes = 0;
	
	private int emptyTicks = 0;
	
	public NeoRegion(int regionX, int regionZ) {
		this.regionX = regionX;
		this.regionZ = regionZ;
		
		for(int i = 0; i < 32; i++) {
			for(int j = 0; j < 32; j++) {
				data[i][j] = new NeoChunk(regionX * 32 + i, regionZ * 32 + j, this);
			}
		}
	}
	
	public static NeoRegion load(int regionX, int regionZ) {
	    return new NeoRegion(regionX, regionZ);
	}
	
	public NeoChunk getChunkAbsolute(int chunkXAbs, int chunkZAbs) {
		return getChunk(chunkXAbs - regionX * 32, chunkZAbs - regionZ * 32);
	}
	
	public NeoChunk getChunk(int x, int z) {
		if(x >= 0 && x < 32 && z >= 0 && z < 32) {
			return data[x][z];
		} else {
			return null;
		}
	}
	
	public void tick() {
		for(int i = 0; i < 32; i++) {
			for(int j = 0; j < 32; j++) {
				NeoChunk chunk = data[i][j];
				if(chunk != null) {
					chunk.tick();
				}
			}
		}
		
		if(meshes == 0) {
		    emptyTicks++;
		} else {
		    emptyTicks = 0;
		}
	}
	
	public void destroy() {
	    for(int i = 0; i < 32; i++) {
            for(int j = 0; j < 32; j++) {
                NeoChunk chunk = data[i][j];
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
    
	public boolean shouldDelete() {
	    return emptyTicks > 100;
    }
	
}
