package makamys.neodymium.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import makamys.neodymium.util.Util;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;

public abstract class Mesh {
	
    /** Can be null, unless gpuStatus is SENT */
	public ByteBuffer buffer;
	public int quadCount;
	public boolean visible;
	public GPUStatus gpuStatus = GPUStatus.UNSENT;
	public int iFirst = -1, iCount = -1;
	public int offset = -1;
	public int pass;
	int x, y, z;
	
	public abstract int getStride();
	
	public double distSq(double x2, double y2, double z2) {
	    return Util.distSq(x, y, z, x2, y2, z2);
	}
	
	public int bufferSize() {
	    return buffer == null ? 0 : buffer.limit();
	}
	
	public int getEnd() {
	    return offset + bufferSize();
	}
	
	public void prepareBuffer() {}
	public void destroyBuffer() {}
	
	public void update() {}
	
	public static enum GPUStatus {
	    UNSENT, SENT, PENDING_DELETE
	}
}
