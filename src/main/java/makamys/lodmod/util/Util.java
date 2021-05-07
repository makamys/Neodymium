package makamys.lodmod.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class Util {
    public static Path getResourcePath(String relPath) {
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
}
