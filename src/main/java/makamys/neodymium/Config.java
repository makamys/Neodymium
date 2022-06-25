package makamys.neodymium;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static makamys.neodymium.Neodymium.LOGGER;
import static makamys.neodymium.Neodymium.MODID;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.lwjgl.input.Keyboard;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

public class Config {

    @ConfigBoolean(cat="_general", def=true, com="Set this to false to fully disable the mod.")
    public static boolean enabled;
    @ConfigBoolean(cat="_general", def=false, com="Apply changes made in the config file immediately without having to manually reload the renderer. Off by default because it could potentially cause poor performance on certain platforms.")
    public static boolean hotswap;
    
    @NeedsReload
    @ConfigBoolean(cat="render", def=false, com="Simplify chunk meshes so they are made of less vertices. Reduces vertex count at the cost of increasing shader complexity. It seems to reduce performance overall.")
    public static boolean simplifyChunkMeshes;
    @ConfigBoolean(cat="render", def=true, com="Don't submit faces for rendering if they are facing away from the camera. Reduces GPU workload at the cost of increasing driver overhead. This will improve the framerate most of the time, but may reduce it if you are not fillrate-limited (such as when playing on a small resolution).")
    public static boolean cullFaces;
    @NeedsReload
    @ConfigBoolean(cat="render", def=false, com="Store texture coordinates as shorts instead of floats. Slightly reduces memory usage and might improve performance by small amount. Might affect visuals slightly, but it's only noticable if the texture atlas is huge.")
    public static boolean shortUV;
    @ConfigInt(cat="render", def=1, min=1, max=Integer.MAX_VALUE, com="Interval (in frames) between the sorting of transparent meshes. Increasing this will reduce CPU usage, but also increase the likelyhood of graphical artifacts appearing when transparent chunks are loaded.")
    public static int sortFrequency;
    
    @NeedsReload
    @ConfigInt(cat="render", def=512, min=1, max=Integer.MAX_VALUE, com="VRAM buffer size (MB). 512 seems to be a good value on Normal render distance. Increase this if you encounter warnings about the VRAM getting full. Does not affect RAM usage.")
    public static int VRAMSize;
    @ConfigBoolean(cat="render", def=true, com="Render fog? Slightly reduces framerate.")
    public static boolean renderFog;
    
    @ConfigBoolean(cat="misc", def=true, com="Replace splash that says 'OpenGL 1.2!' with 'OpenGL 3.3!'. Just for fun.")
    public static boolean replaceOpenGLSplash;
    
    @ConfigInt(cat="debug", def=-1, min=-1, max=Integer.MAX_VALUE)
    public static int maxMeshesPerFrame;
    @ConfigInt(cat="debug", def=Keyboard.KEY_F4, min=-1, max=Integer.MAX_VALUE, com="The LWJGL keycode of the key that has to be held down while pressing the debug keybinds. Setting this to 0 will make the keybinds usable without holding anything else down. Setting this to -1 will disable debug keybinds entirely.")
    public static int debugPrefix;
    @ConfigInt(cat="debug", def=80, min=-1, max=Integer.MAX_VALUE, com="The Y position of the first line of the debug info in the F3 overlay. Set this to -1 to disable showing that info.")
    public static int debugInfoStartY;
    @ConfigBoolean(cat="debug", def=false)
    public static boolean wireframe;
    
    private static File configFile = new File(Launch.minecraftHome, "config/" + MODID + ".cfg");
    private static WatchService watcher;
    
    public static void reloadConfig(ReloadInfo info) {
        try {
            if(Files.size(configFile.toPath()) == 0) {
                // Sometimes the watcher fires twice, and the first time the file is empty.
                // I don't know why. This is the workaround.
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        Configuration config = new Configuration(configFile, Neodymium.VERSION);
        
        config.load();
        
        boolean needReload = loadFields(config);
        if(info != null) {
            info.needReload = needReload;
        }
        
        if(config.hasChanged() || (!config.getLoadedConfigVersion().equals(config.getDefinedConfigVersion()))) {
            config.save();
        }
        
        if(hotswap && watcher == null) {
            try {
                registerWatchService();
            } catch(IOException e) {
                LOGGER.warn("Failed to register watch service: " + e + " (" + e.getMessage() + "). Changes to the config file will not be reflected");
            }
        }
    }
    
    public static void reloadConfig() {
        reloadConfig(null);
    }
    
    private static boolean loadFields(Configuration config) {
        boolean needReload = false;
        
        for(Field field : Config.class.getFields()) {
            if(!Modifier.isStatic(field.getModifiers())) continue;
            
            NeedsReload needsReload = null;
            ConfigBoolean configBoolean = null;
            ConfigInt configInt = null;
            
            for(Annotation an : field.getAnnotations()) {
                if(an instanceof NeedsReload) {
                    needsReload = (NeedsReload) an;
                } else if(an instanceof ConfigInt) {
                    configInt = (ConfigInt) an;
                } else if(an instanceof ConfigBoolean) {
                    configBoolean = (ConfigBoolean) an;
                }
            }
            
            if(configBoolean == null && configInt == null) continue;
            
            Object currentValue = null;
            Object newValue = null;
            try {
                currentValue = field.get(null);
            } catch (Exception e) {
                LOGGER.error("Failed to get value of field " + field.getName());
                e.printStackTrace();
                continue;
            }
            
            if(configBoolean != null) {
                newValue = config.getBoolean(field.getName(), configBoolean.cat(), configBoolean.def(), configBoolean.com());
            } else if(configInt != null) {
                newValue = config.getInt(field.getName(), configInt.cat(), configInt.def(), configInt.min(), configInt.max(), configInt.com()); 
            }
            
            if(needsReload != null && !newValue.equals(currentValue)) {
                needReload = true;
            }
            
            try {
                field.set(null, newValue);
            } catch (Exception e) {
                LOGGER.error("Failed to set value of field " + field.getName());
                e.printStackTrace();
            }
        }
        
        return needReload;
    }
    
    public static boolean reloadIfChanged(ReloadInfo info) {
        boolean reloaded = false;
        if(watcher != null) {
            WatchKey key = watcher.poll();
            
            if(key != null) {
                for(WatchEvent<?> event: key.pollEvents()) {
                    if(event.context().toString().equals(configFile.getName())) {
                        reloadConfig(info);
                        reloaded = true;
                    }
                }
                key.reset();
            }
        }
        
        return reloaded;
    }
    
    private static void registerWatchService() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        configFile.toPath().getParent().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface NeedsReload {

    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface ConfigBoolean {

        String cat();
        boolean def();
        String com() default "";

    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface ConfigInt {

        String cat();
        int min();
        int max();
        int def();
        String com() default "";

    }
    
    public static class ReloadInfo {
        boolean needReload;
    }
    
}
