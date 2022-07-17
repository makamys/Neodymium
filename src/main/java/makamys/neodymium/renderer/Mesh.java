package makamys.neodymium.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import makamys.neodymium.util.Util;

/** A generic mesh that can be sent to the GPU for rendering. */
public abstract class Mesh {
	
    /** Can be null, unless gpuStatus is SENT */
	public ByteBuffer buffer;
	public int quadCount;
	public boolean visible;
	public GPUStatus gpuStatus = GPUStatus.UNSENT;
	public int iFirst = -1, iCount = -1;
	public long offset = -1;
	public int pass;
	int x, y, z;
	public QuadNormal normal = QuadNormal.NONE;
	
	public abstract int getStride();
	
	public double distSq(double x2, double y2, double z2) {
	    return Util.distSq(x + 0.5, y + 0.5, z + 0.5, x2, y2, z2);
	}
	
	public int bufferSize() {
	    return buffer == null ? 0 : buffer.limit();
	}
	
	public long getEnd() {
	    return offset + bufferSize();
	}
	
	public void prepareBuffer() {}
	public void destroyBuffer() {}
	
	public void update() {}
	
	public static enum GPUStatus {
	    UNSENT, SENT, PENDING_DELETE
	}

    public int writeToIndexBuffer(IntBuffer piFirst, IntBuffer piCount, int cameraXDiv, int cameraYDiv, int cameraZDiv, int pass) {
        piFirst.put(iFirst);
        piCount.put(iCount);
        
        return 1;
    }
}
