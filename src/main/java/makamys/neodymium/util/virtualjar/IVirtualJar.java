package makamys.neodymium.util.virtualjar;

import java.io.InputStream;

public interface IVirtualJar {

    public String getName();
    
    public InputStream getInputStream(String path);
    
}
