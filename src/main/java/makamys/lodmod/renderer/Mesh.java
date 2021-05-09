package makamys.lodmod.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;

public abstract class Mesh {
	
	public ByteBuffer buffer;
	public int quadCount;
	public boolean visible;
	public boolean pendingGPUDelete;
	public int iFirst = -1, iCount = -1;
	public int offset = -1;
	public int pass;
	
	public abstract int getStride();
	public abstract double distSq(Entity player);
}
