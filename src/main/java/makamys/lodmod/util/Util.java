package makamys.lodmod.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.launchwrapper.Launch;

public class Util {
    
    private static boolean allowResourceOverrides = Boolean.parseBoolean(System.getProperty("lodmod.allowResourceOverrides", "false"));
    
    public static Path getResourcePath(String relPath) {
        if(allowResourceOverrides) {
            File overrideFile = new File(new File(Launch.minecraftHome, "lodmod/resources"), relPath);
            if(overrideFile.exists()) {
                return overrideFile.toPath();
            }
        }
        
        try {
            URL resourceURL = Util.class.getClassLoader().getResource(relPath);
            
            switch(resourceURL.getProtocol()) {
            case "jar":
                String urlString = resourceURL.getPath();
                int lastExclamation = urlString.lastIndexOf('!');
                String newURLString = urlString.substring(0, lastExclamation);
                return FileSystems.newFileSystem(new File(URI.create(newURLString)).toPath(), null).getPath(relPath);
            case "file":
                return new File(URI.create(resourceURL.toString())).toPath();
            default:
                return null;
            }
        } catch(IOException e) {
            return null;
        }
    }
    
    public static String readFile(String path){
        try {
            return new String(Files.readAllBytes(Util.getResourcePath(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    public static byte[] byteBufferToArray(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.limit()];
        int pos = buffer.position();
        buffer.position(0);
        buffer.get(dst);
        buffer.position(pos);
        return dst;
    }
    
    public static int[] intBufferToArray(IntBuffer buffer) {
        int[] dst = new int[buffer.remaining()];
        buffer.get(dst);
        buffer.flip();
        return dst;
    }
    
    public static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.pow(x1 - x2, 2) +
                Math.pow(y1 - y2, 2) +
                Math.pow(z1 - z2, 2);
    }
}
