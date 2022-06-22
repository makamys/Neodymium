package makamys.neodymium.renderer;

import java.util.List;

import makamys.neodymium.Config;
import makamys.neodymium.Neodymium;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;

/** A container for the meshes that compose a chunk (16x256x16 region). It keeps track of which meshes should be made visible and which ones should not. */
public class NeoChunk {
	
	int x, z;
	public boolean needsChunk = true;
	int lod = 0;
	boolean visible;
	boolean dirty;
	boolean discardedMesh;
	
	SimpleChunkMesh[] simpleMeshes = new SimpleChunkMesh[2];
	CullableMeshCollection[] chunkMeshes = new CullableMeshCollection[32];
	
	public boolean[] isSectionVisible = new boolean[16];
	
	NeoRenderer renderer = Neodymium.renderer;
	
	public NeoChunk(int x, int z) {
		this.x = x;
		this.z = z;
	}
	/*
	public LODChunk(NBTTagCompound nbt, List<String> spriteList) {
	    this.x = nbt.getInteger("x");
	    this.z = nbt.getInteger("z");
	    
	    loadChunkMeshesNBT(nbt.getCompoundTag("chunkMeshes"), spriteList);
	}
	
	private void loadChunkMeshesNBT(NBTTagCompound chunkMeshesCompound, List<String> spriteList) {
	    for(Object o : chunkMeshesCompound.func_150296_c()) {
            String key = (String)o;
            int keyInt = Integer.parseInt(key);
            
            byte[] data = chunkMeshesCompound.getByteArray(key);
            
            chunkMeshes[keyInt] = new ChunkMesh(x, keyInt / 2, z, new ChunkMesh.Flags(true, true, true, false), data.length / (2 + 4 * (3 + 2 + 2 + 4)), data, spriteList, keyInt % 2);
        }
	}
	*/
	@Override
	public String toString() {
		return "LODChunk(" + x + ", " + z + ")";
	}
	
	public double distSq(Entity entity) {
		return Math.pow(entity.posX - x * 16, 2) + Math.pow(entity.posZ - z * 16, 2);
	}
	
	public void putChunkMeshes(int cy, List<CullableMeshCollection> newChunkMeshes) {
		for(int i = 0; i < 2; i++) {
		    CullableMeshCollection newChunkMesh = newChunkMeshes.size() > i ? newChunkMeshes.get(i) : null;
		    if(chunkMeshes[cy * 2 + i] != null) {
			    if(newChunkMesh != null) {
			        // ??? why is this needed?
			        for(ChunkMesh mesh : newChunkMesh.getMeshes()) {
			            if(mesh != null) {
			                mesh.pass = i;
			            }
			        }
			    }
			    
			    renderer.removeMesh(chunkMeshes[cy * 2 + i]);
			    chunkMeshes[cy * 2 + i].destroy();
			}
		    chunkMeshes[cy * 2 + i] = newChunkMesh;
		}
		Neodymium.renderer.lodChunkChanged(this);
		dirty = true;
		discardedMesh = false;
	}
	
	// nice copypasta
	public void putSimpleMeshes(List<SimpleChunkMesh> newSimpleMeshes) {
	    for(int i = 0; i < 2; i++) {
            SimpleChunkMesh newSimpleMesh = newSimpleMeshes.size() > i ? newSimpleMeshes.get(i) : null;
            if(simpleMeshes[i] != null) {
                if(newSimpleMesh != null) {
                    newSimpleMesh.pass = i;
                }
                
                renderer.setMeshVisible(simpleMeshes[i], false);
                simpleMeshes[i].destroy();
            }
            simpleMeshes[i] = newSimpleMesh;
        }
	    Neodymium.renderer.lodChunkChanged(this);
	}
	
	public boolean hasChunkMeshes() {
		for(CullableMeshCollection cm : chunkMeshes) {
			if(cm != null) {
				return true;
			}
		}
		return false;
	}
	
	public void tick(Entity player) {
		double distSq = distSq(player);
		if(Config.disableSimpleMeshes || distSq < Math.pow((Neodymium.renderer.renderRange / 2) * 16, 2)) {
		    setLOD(2);
		} else if(distSq < Math.pow((Neodymium.renderer.renderRange) * 16, 2)) {
		    setLOD(1);
		} else {
		    setLOD(0);
		}
	}
	
   public void setLOD(int lod) {
        if(lod == this.lod) return;
        
        this.lod = lod;
        Neodymium.renderer.lodChunkChanged(this);
        if(!dirty) {
            if(lod < 2) {
                for(int i = 0; i < chunkMeshes.length; i++) {
                    if(chunkMeshes[i] != null) {
                        chunkMeshes[i].destroy();
                        chunkMeshes[i] = null;
                        discardedMesh = true;
                    }
                }
            }
        }
    }
	/*
	public NBTTagCompound saveToNBT(NBTTagCompound oldNbt, List<String> oldStringTable) {
	    NBTTagCompound nbt = new NBTTagCompound();
	    nbt.setInteger("x", x);
	    nbt.setInteger("z", z);
	    
	    NBTTagCompound chunkMeshesCompound = oldNbt == null ? new NBTTagCompound() : oldNbt.getCompoundTag("chunkMeshes");
	    if(!discardedMesh) {
	        for(int i = 0; i < chunkMeshes.length; i++) {
	            if(chunkMeshes[i] != null) {
	                chunkMeshesCompound.setTag(String.valueOf(i), chunkMeshes[i].nbtData);
	            }
	        }
	    } else if(oldNbt != null && discardedMesh && lod == 2) {
	        loadChunkMeshesNBT(chunkMeshesCompound, oldStringTable);
	        Neodymium.renderer.lodChunkChanged(this);
	    }
	    nbt.setTag("chunkMeshes", chunkMeshesCompound);
	    dirty = false;
	    return nbt;
	}
	*/
	public void destroy() {
	    for(SimpleChunkMesh scm: simpleMeshes) {
	        if(scm != null) {
	            scm.destroy();
	        }
        }
	    for(CullableMeshCollection cm: chunkMeshes) {
	        if(cm != null) {
	            cm.destroy();
	        }
	    }
	    Neodymium.renderer.setVisible(this, false);
	}
	
	public void receiveChunk(Chunk chunk) {
	    if(!Config.disableSimpleMeshes) {
	        putSimpleMeshes(SimpleChunkMesh.generateSimpleMeshes(chunk));
	    }
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
        for(SimpleChunkMesh scm: simpleMeshes) {
            if(scm != null) {
                return false;
            }
        }
        for(CullableMeshCollection cm: chunkMeshes) {
            if(cm != null) {
                return false;
            }
        }
        return true;
    }
	
}
