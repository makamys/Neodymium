package makamys.neodymium.renderer;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import makamys.neodymium.util.Util;
import net.minecraft.entity.Entity;

public class NeoRegion {
    public static final int SIZE = 64;
	
	private NeoChunk[][] data = new NeoChunk[SIZE][SIZE];
	@Getter
	private List<RenderData> renderData = new ArrayList<>();
	
	int regionX, regionZ;
	
	public int meshes = 0;
	
	private int emptyTicks = 0;

	public RenderData getRenderData(GPUMemoryManager manager) {
		int index = manager.managerIndex;
		while (renderData.size() <= index) {
			renderData.add(new RenderData(regionX * SIZE * 16, 0, regionZ * SIZE * 16));
		}
		return renderData.get(index);
	}
	
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
	
	public double distSq(double x, double y, double z) {
        return Util.distSq(regionX + 0.5, 0, regionZ + 0.5, x, y, z);
    }

    public static float toRelativeOffset(double d) {
        return (float)(d - (Math.floor(d / (SIZE * 16.0)) * SIZE * 16.0));
    }
	
	@Override
	public String toString() {
	    return "NeoRegion(" + regionX + ", " + regionZ + ")[n=" + meshes + "]";
	}
    
    public boolean shouldDelete() {
        return emptyTicks > 100;
    }
    
    @RequiredArgsConstructor
    public static class RenderData {
        public final double originX, originY, originZ;
        
        @Getter
		private final List<Mesh> sentMeshes = new ArrayList<Mesh>();
        public int batchLimit;
        public int batchFirst;
        
        public void sort(double eyePosX, double eyePosY, double eyePosZ, boolean pass0, boolean pass1) {
            if(pass0) {
                sentMeshes.sort(Comparators.MESH_DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ).setInverted(false));
            }
            if(pass1) {
                sentMeshes.sort(Comparators.MESH_DISTANCE_COMPARATOR.setOrigin(eyePosX, eyePosY, eyePosZ).setInverted(true));
            }
        }
	}
}
