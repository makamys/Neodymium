package makamys.lodmod.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import makamys.lodmod.util.Util;
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
	int x, y, z;
	
	public abstract int getStride();
	public void onVisibilityChanged() {}
	
	public double distSq(double x2, double y2, double z2) {
	    return Util.distSq(x, y, z, x2, y2, z2);
	}
	
	public int bufferSize() {
	    int bufferSize = quadCount * 6 * getStride();
	    if(buffer != null) {
	        assert buffer.limit() == bufferSize;
	    }
	    return bufferSize;
	}
}
