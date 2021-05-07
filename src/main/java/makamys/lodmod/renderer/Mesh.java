package makamys.lodmod.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public abstract class Mesh {
	
	public ByteBuffer buffer;
	public int quadCount;
	public boolean visible;
	public boolean pendingGPUDelete;
	public int iFirst = -1, iCount = -1;
	public int offset = -1;
	
	public abstract int getStride();
	
}
