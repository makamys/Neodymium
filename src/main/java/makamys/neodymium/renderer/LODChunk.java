package makamys.neodymium.renderer;

import java.util.List;

import makamys.neodymium.LODMod;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;

public class LODChunk {
	
	int x, z;
	public boolean needsChunk = true;
	int lod = 0;
	boolean visible;
	boolean dirty;
	boolean discardedMesh;
	
	SimpleChunkMesh[] simpleMeshes = new SimpleChunkMesh[2];
	ChunkMesh[] chunkMeshes = new ChunkMesh[32];
	
	public boolean[] isSectionVisible = new boolean[16];
	
	LODRenderer renderer = LODMod.renderer;
	
	public LODChunk(int x, int z) {
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
	
	public void putChunkMeshes(int cy, List<ChunkMesh> newChunkMeshes) {
		for(int i = 0; i < 2; i++) {
		    ChunkMesh newChunkMesh = newChunkMeshes.size() > i ? newChunkMeshes.get(i) : null;
		    if(chunkMeshes[cy * 2 + i] != null) {
			    if(newChunkMesh != null) {
			        newChunkMesh.pass = i;
			    }
			    
			    renderer.removeMesh(chunkMeshes[cy * 2 + i]);
			    chunkMeshes[cy * 2 + i].destroy();
			}
		    chunkMeshes[cy * 2 + i] = newChunkMesh;
		}
		LODMod.renderer.lodChunkChanged(this);
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
	    LODMod.renderer.lodChunkChanged(this);
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
		if(LODMod.disableSimpleMeshes || distSq < Math.pow((LODMod.renderer.renderRange / 2) * 16, 2)) {
		    setLOD(2);
		} else if(distSq < Math.pow((LODMod.renderer.renderRange) * 16, 2)) {
		    setLOD(1);
		} else {
		    setLOD(0);
		}
	}
	
   public void setLOD(int lod) {
        if(lod == this.lod) return;
        
        this.lod = lod;
        LODMod.renderer.lodChunkChanged(this);
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
	        LODMod.renderer.lodChunkChanged(this);
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
	    for(ChunkMesh cm: chunkMeshes) {
	        if(cm != null) {
	            cm.destroy();
	        }
	    }
	    LODMod.renderer.setVisible(this, false);
	}
	
	public void receiveChunk(Chunk chunk) {
	    if(!LODMod.disableSimpleMeshes) {
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
	
}
