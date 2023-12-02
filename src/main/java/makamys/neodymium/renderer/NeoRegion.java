package makamys.neodymium.renderer;

import net.minecraft.entity.Entity;

public class NeoRegion {
    public static final int SIZE = 32;
	
	private NeoChunk[][] data = new NeoChunk[SIZE][SIZE];
	
	int regionX, regionZ;
	
	public int meshes = 0;
	
	private int emptyTicks = 0;
	
	public NeoRegion(int regionX, int regionZ) {
		this.regionX = regionX;
		this.regionZ = regionZ;
		
		for(int i = 0; i < SIZE; i++) {
			for(int j = 0; j < SIZE; j++) {
				data[i][j] = new NeoChunk(regionX * SIZE + i, regionZ * SIZE + j, this);
			}
		}
	}
	
	public static NeoRegion load(int regionX, int regionZ) {
	    return new NeoRegion(regionX, regionZ);
	}
	
	public NeoChunk getChunkAbsolute(int chunkXAbs, int chunkZAbs) {
		return getChunk(chunkXAbs - regionX * SIZE, chunkZAbs - regionZ * SIZE);
	}
	
	public NeoChunk getChunk(int x, int z) {
		if(x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
			return data[x][z];
		} else {
			return null;
		}
	}
	
	public void tick() {
		for(int i = 0; i < SIZE; i++) {
			for(int j = 0; j < SIZE; j++) {
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
	    for(int i = 0; i < SIZE; i++) {
            for(int j = 0; j < SIZE; j++) {
                NeoChunk chunk = data[i][j];
                if(chunk != null) {
                    chunk.destroy();
                }
            }
        }
	}
	
	public double distanceTaxicab(Entity entity) {
	    double centerX = ((regionX * SIZE) + SIZE / 2) * 16;
	    double centerZ = ((regionZ * SIZE) + SIZE / 2) * 16;
	    
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
