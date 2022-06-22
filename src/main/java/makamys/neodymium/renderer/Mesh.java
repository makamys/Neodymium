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
	public int offset = -1;
	public int pass;
	int x, y, z;
	public QuadNormal normal = QuadNormal.NONE;
	
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

    public void writeToIndexBuffer(IntBuffer piFirst, IntBuffer piCount, int[] renderedMeshesReturn,
            int[] renderedQuadsReturn) {
        renderedMeshesReturn[0] = 1;
        renderedQuadsReturn[0] = quadCount;
        piFirst.put(iFirst);
        piCount.put(iCount);
    }
}
