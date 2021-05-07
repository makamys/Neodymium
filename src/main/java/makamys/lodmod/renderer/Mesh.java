package makamys.lodmod.renderer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public abstract class Mesh {
	
	public ByteBuffer buffer;
	public int quadCount;
	public boolean visible;
	public int iFirst, iCount;
	
	public abstract int getStride();
	
}
