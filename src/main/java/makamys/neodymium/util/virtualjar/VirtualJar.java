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

/**
 * This class is used to register fake "virtual" jars and add them to LaunchClassLoader's class path. The contents of
 * these jars are determined dynamically by the implementation of {@link IVirtualJar}, rather than a real file in the file system.
 */
public class VirtualJar {
    
    private static boolean registered;
    
    private static Map<String, IVirtualJar> jars = new HashMap<>();
    
    public static void registerHandler() {
        if(registered) return;
        
        LOGGER.debug("Registering URL protocol handler: " + PROTOCOL);
        
        // We want the Handler to always be loaded by the same class loader.
        Launch.classLoader.addClassLoaderExclusion(getPackage() + ".protocol." + PROTOCOL + ".");
        
        // The Handler is loaded by the AppClassLoader, but it needs to access the state of VirtualJar, which is loaded
        // by the LaunchClassLoader. The solution? Make the Handler just a proxy that delegates the real work to
        // VirtualJar.StreamHandlerImpl. We use the blackboard as a class loader-agnostic way of sharing information.
        Handler.IURLStreamHandlerImpl streamHandlerImpl = new StreamHandlerImpl();
        Launch.blackboard.put(MODID + "." + PROTOCOL + ".impl", streamHandlerImpl);
        URLStreamHandlerHelper.register(Handler.class);
        
        registered = true;
    }
    
    private static String getPackage() {
        String name = VirtualJar.class.getName();
        return name.substring(0, name.lastIndexOf('.'));
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
        public URLConnection openConnection(URL url) throws IOException {
            return new VirtualJarConnection(url);
        }
        
        public static class VirtualJarConnection extends URLConnection {
            
            IVirtualJar jar;
            private String filePath;
            
            public VirtualJarConnection(URL url) throws IOException {
                super(url);
                
                String path = url.getPath();
                String nameSuffix = ".jar!";
                int nameEnd = path.indexOf(nameSuffix);
                String name = path.substring(0, nameEnd);
                
                jar = jars.get(name);
                
                if(jar == null) {
                    throw new IOException();
                }
                
                filePath = path.substring(nameEnd + nameSuffix.length());
                
                if(!jar.hasFile(filePath)) {
                    throw new IOException();
                }
            }
            
            public void connect() throws IOException {
                
            }

            public Object getContent() throws IOException {
                return super.getContent();
            }

            public String getHeaderField(String name) {
                return super.getHeaderField(name);
            }

            public InputStream getInputStream() {
                return jar.getInputStream(filePath);
            }

            public java.io.OutputStream getOutputStream() throws IOException {
                return super.getOutputStream();
            }
            
        }
    }
    
}
