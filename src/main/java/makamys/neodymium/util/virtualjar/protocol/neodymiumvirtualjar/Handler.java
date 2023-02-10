package makamys.neodymium.util.virtualjar.protocol.neodymiumvirtualjar;

import static makamys.neodymium.Constants.MODID;
import static makamys.neodymium.Constants.PROTOCOL;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import net.minecraft.launchwrapper.Launch;

public class Handler extends URLStreamHandler {

    private static final IURLStreamHandlerImpl impl = (IURLStreamHandlerImpl)Launch.blackboard.get(MODID + "." + PROTOCOL + ".impl");

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return impl.openConnection(url);
    }

    public interface IURLStreamHandlerImpl {
        URLConnection openConnection(URL url) throws IOException;
    }
}
