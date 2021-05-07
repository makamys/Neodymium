package makamys.lodmod.renderer;

import java.util.List;

import makamys.lodmod.LODMod;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;

public class LODChunk {
	
	int x, z;
	Chunk chunk;
	public boolean waitingForData = false;
	int lod = 0;
	boolean visible;
	
	SimpleChunkMesh simpleMesh;
	ChunkMesh[] chunkMeshes = new ChunkMesh[32];
	
	LODRenderer renderer = LODMod.renderer;
	
	public LODChunk(int x, int z) {
		this.x = x;
		this.z = z;
	}
	
	@Override
	public String toString() {
		return "LODChunk(" + x + ", " + z + ")";
	}
	
	public double distSq(Entity entity) {
		return Math.pow(entity.posX - x * 16, 2) + Math.pow(entity.posZ - z * 16, 2);
	}
	
	public void putChunkMeshes(int cy, List<ChunkMesh> newChunkMeshes) {
		for(int i = 0; i < 2; i++) {
			if(chunkMeshes[cy * 2 + i] != null) {
			    renderer.setMeshVisible(chunkMeshes[cy * 2 + i], false);
			    chunkMeshes[cy * 2 + i].destroy();
				chunkMeshes[cy * 2 + i] = null;
			}
		}
		
		for(int i = 0; i < newChunkMeshes.size(); i++) {
			chunkMeshes[cy * 2 + i] = newChunkMeshes.get(i);
		}
	}
	
	public boolean hasChunkMeshes() {
		for(ChunkMesh cm : chunkMeshes) {
			if(cm != null) {
				return true;
			}
		}
		return false;
	}
	
	public void tick(Entity player) {
		double distSq = distSq(player);
		if(distSq < Math.pow(32 * 16, 2)) {
		    renderer.setLOD(this, 2);
		} else if(distSq < Math.pow(64 * 16, 2)) {
		    renderer.setLOD(this, 1);
		} else {
		    renderer.setLOD(this, 0);
		}
	}
	
}
