package makamys.neodymium.renderer;

import java.util.List;

import makamys.neodymium.Neodymium;
import net.minecraft.entity.Entity;

/** A container for the meshes that compose a chunk (16x256x16 region). It keeps track of which meshes should be made visible and which ones should not. */
public class NeoChunk {
	
	int x, z;
	int lod = 0;
	boolean visible;
	boolean dirty;
	NeoRegion region;
	
	ChunkMesh[] chunkMeshes = new ChunkMesh[32];
	
	public boolean[] isSectionVisible = new boolean[16];
	
	NeoRenderer renderer = Neodymium.renderer;
	
	public NeoChunk(int x, int z, NeoRegion region) {
		this.x = x;
		this.z = z;
		this.region = region;
	}
	
	@Override
	public String toString() {
		return "NeoChunk(" + x + ", " + z + ")";
	}
	
	public double distSq(Entity entity) {
		return Math.pow(entity.posX - x * 16, 2) + Math.pow(entity.posZ - z * 16, 2);
	}
	
	public void putChunkMeshes(int cy, List<ChunkMesh> newChunkMeshes, boolean addOnly) {
		for(int i = 0; i < 2; i++) {
		    ChunkMesh newChunkMesh = newChunkMeshes.size() > i ? newChunkMeshes.get(i) : null;
		    
		    if(newChunkMesh != null || !addOnly) {
    		    if(chunkMeshes[cy * 2 + i] != null) {
    			    renderer.removeMesh(chunkMeshes[cy * 2 + i]);
    			    chunkMeshes[cy * 2 + i].destroy();
    			} else {
    			    region.meshes++;
    			}
    		    chunkMeshes[cy * 2 + i] = newChunkMesh;
    		    dirty = true;
		    }
		}
		if(dirty) {
		    Neodymium.renderer.neoChunkChanged(this);
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
	
	public void tick() {
        setLOD(2);
	}
	
   public void setLOD(int lod) {
        if(lod == this.lod) return;
        
        this.lod = lod;
        Neodymium.renderer.neoChunkChanged(this);
        if(!dirty) {
            if(lod < 2) {
                for(int i = 0; i < chunkMeshes.length; i++) {
                    if(chunkMeshes[i] != null) {
                        chunkMeshes[i].destroy();
                        chunkMeshes[i] = null;
                        region.meshes--;
                    }
                }
            }
        }
    }
   
	public void destroy() {
	    for(ChunkMesh cm: chunkMeshes) {
	        if(cm != null) {
	            cm.destroy();
	            region.meshes--;
	        }
	    }
	    Neodymium.renderer.setVisible(this, false);
	}
	
	public boolean isFullyVisible() {
	    if(!visible) return false;
	    for(boolean b : isSectionVisible) {
	        if(!b) {
	            return false;
	        }
	    }
	    return true;
	}
	
    public boolean isEmpty() {
        for(ChunkMesh cm: chunkMeshes) {
            if(cm != null) {
                return false;
            }
        }
        return true;
    }
	
}
