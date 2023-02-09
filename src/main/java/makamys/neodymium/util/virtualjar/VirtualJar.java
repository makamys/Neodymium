package makamys.neodymium.util.virtualjar;

import static makamys.neodymium.Constants.MODID;
import static makamys.neodymium.Constants.PROTOCOL;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import static makamys.neodymium.Constants.LOGGER;

import makamys.neodymium.util.virtualjar.protocol.neodymiumvirtualjar.Handler;
import net.minecraft.launchwrapper.Launch;


public class VirtualJar {
    
    private static boolean registered;
    
    private static Map<String, IVirtualJar> jars = new HashMap<>();
    
    public static void registerHandler() {
        if(registered) return;
        
        LOGGER.debug("Registering URL protocol handler: " + PROTOCOL);
        
        // We want the Handler to always be loaded by the same class loader.
        Launch.classLoader.addClassLoaderExclusion("makamys." + MODID + ".util.virtualjar.protocol." + PROTOCOL);
        
        // The Handler is loaded by the AppClassLoader, but it needs to access the state of VirtualJar, which is loaded
        // by the LaunchClassLoader. The solution? Make the Handler just a proxy that delegates the real work to
        // VirtualJar.StreamHandlerImpl. We use the blackboard as a class loader-agnostic way of sharing information.
        Handler.IURLStreamHandlerImpl streamHandlerImpl = new StreamHandlerImpl();
        Launch.blackboard.put(MODID + "." + PROTOCOL + ".impl", streamHandlerImpl);
        URLStreamHandlerHelper.register(Handler.class);
        
        registered = true;
    }
    
    public static void add(IVirtualJar jar) {
        registerHandler();
        
        LOGGER.trace("Adding virtual jar to class path: " + PROTOCOL + ":" + jar.getName() + ".jar");
        
        String urlStr = PROTOCOL + ":" + jar.getName() + ".jar!/";
        
        try {
            URL url = new URL(urlStr);
            Launch.classLoader.addURL(url);
            // Forge expects all URLs in the sources list to be convertible to File objects, so we must remove it to
            // avoid a crash.
            Launch.classLoader.getSources().remove(url);
            
            jars.put(jar.getName(), jar);
        } catch(MalformedURLException e) {
            LOGGER.fatal("Failed to add virtual jar to class path");
            e.printStackTrace();
        }
    }
    
    public static class StreamHandlerImpl implements Handler.IURLStreamHandlerImpl {

        @Override
        public URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                public void connect() {
                    
                }

                public Object getContent() throws IOException {
                    return super.getContent();
                }

                public String getHeaderField(String name) {
                    return super.getHeaderField(name);
                }

                public InputStream getInputStream() {
                    String path = getURL().getPath();
                    String nameSuffix = ".jar!";
                    int nameEnd = path.indexOf(nameSuffix);
                    String name = path.substring(0, nameEnd);
                    
                    IVirtualJar jar = jars.get(name);
                    
                    return jar.getInputStream(path.substring(nameEnd + nameSuffix.length()));
                }

                public java.io.OutputStream getOutputStream() throws IOException {
                    return super.getOutputStream();
                }
            };
        }
    }
    
}
