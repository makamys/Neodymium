package makamys.neodymium.util.virtualjar;

import java.io.InputStream;

public interface IVirtualJar {

    public String getName();
    
    public default boolean hasFile(String path) {
        try(InputStream is = getInputStream(path)) {
            return is != null;
        } catch(Exception e) {
            return false;
        }
    }
    
    public InputStream getInputStream(String path);
    
}
