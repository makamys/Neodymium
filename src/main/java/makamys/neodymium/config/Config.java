package makamys.neodymium.config;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static makamys.neodymium.Constants.LOGGER;
import static makamys.neodymium.Constants.MODID;
import static makamys.neodymium.Constants.VERSION;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.EnumUtils;
import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.config.IConfigElement;
import makamys.neodymium.Neodymium;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.config.Property.Type;

public class Config {

    @ConfigBoolean(cat="_general", def=true, com="Set this to false to fully disable the mod.")
    public static boolean enabled;
    @ConfigBoolean(cat="_general", def=false, com="Apply changes made in the config file immediately without having to manually reload the renderer. Off by default because it could potentially cause poor performance on certain platforms.")
    public static boolean hotswap;
    @ConfigBoolean(cat="_general", def=true, com="Set this to false to disable update checking.")
    public static boolean updateChecker;

    @ConfigBoolean(cat="render", def=true, com="Don't submit faces for rendering if they are facing away from the camera. Reduces GPU workload at the cost of increasing driver overhead. This will improve the framerate most of the time, but may reduce it if you are not fillrate-limited (such as when playing on a small resolution).")
    public static boolean cullFaces;
    @NeedsReload
    @ConfigBoolean(cat="render", def=false, com="Store texture coordinates as shorts instead of floats. Slightly reduces memory usage and might improve performance by small amount. Might affect visuals slightly, but it's only noticable if the texture atlas is huge.\nDoes nothing if OptiFine with shaders or RPLE is present.")
    public static boolean shortUV;
    @ConfigInt(cat="render", def=1, min=1, max=Integer.MAX_VALUE, com="Interval (in frames) between the sorting of transparent meshes. Increasing this will reduce CPU usage, but also increase the likelyhood of graphical artifacts appearing when transparent chunks are loaded.")
    public static int sortFrequency;
    @ConfigBoolean(cat="render", def=true, com="Don't render meshes that are shrouded in fog. OptiFine also does this when fog is turned on, this setting makes Neodymium follow suit.")
    public static boolean fogOcclusion;
    @ConfigBoolean(cat="render", def=false, com="Do fog occlusion even if fog is disabled.")
    public static boolean fogOcclusionWithoutFog;
    
    @ConfigEnum(cat="render", def="auto", clazz=AutomatableBoolean.class, com="Render fog? Slightly reduces framerate. `auto` means the OpenGL setting will be respected (as set by mods like OptiFine).\nValid values: true, false, auto")
    public static AutomatableBoolean renderFog;
    @ConfigInt(cat="render", def=Integer.MAX_VALUE, min=0, max=Integer.MAX_VALUE, com="Chunks further away than this distance (in chunks) will not have unaligned quads such as tall grass rendered.")
    public static int maxUnalignedQuadDistance;
    @ConfigInt(cat="render", def=256,min=16,max=1024,com = "The size of the allocation chunks for opaque geometry (in Megabytes). Requires game restart to apply.")
    public static int bufferSizePass0;
    @ConfigInt(cat="render", def=64,min=16,max=1024,com = "The size of the allocation chunks for transparent geometry (in Megabytes). Requires game restart to apply.")
    public static int bufferSizePass1;

    @ConfigBoolean(cat="misc", def=true, com="Replace splash that says 'OpenGL 1.2!' with 'OpenGL 3.3!'. Just for fun.")
    public static boolean replaceOpenGLSplash;
    @ConfigBoolean(cat="misc", def=false, com="Don't warn about incompatibilities in chat, and activate renderer even in spite of critical ones.")
    public static boolean ignoreIncompatibilities;
    @ConfigBoolean(cat="misc", def=false, com="Don't print non-critical rendering errors.")
    public static boolean silenceErrors;
    
    @ConfigInt(cat="debug", def=-1, min=-1, max=Integer.MAX_VALUE)
    public static int maxMeshesPerFrame;
    @ConfigInt(cat="debug", def=Keyboard.KEY_F4, min=-1, max=Integer.MAX_VALUE, com="The LWJGL keycode of the key that has to be held down while pressing the debug keybinds. Setting this to 0 will make the keybinds usable without holding anything else down. Setting this to -1 will disable debug keybinds entirely.")
    public static int debugPrefix;
    @ConfigBoolean(cat="debug", def=true, com="Set this to false to stop showing the debug info in the F3 overlay.")
    public static boolean showDebugInfo;
    @ConfigBoolean(cat="debug", def=false)
    public static boolean wireframe;
    @ConfigBoolean(cat="debug", def=false, com="Enable building of vanilla chunk meshes. Makes it possible to switch to the vanilla renderer on the fly, at the cost of reducing chunk update performance. Also fixes compatibility with Factorization (see issue #49).\nCompatibility note: Not compatible with FalseTweaks, so enabling this will have no effect if FalseTweaks is present.")
    public static boolean enableVanillaChunkMeshes;
    
    private static Configuration config;
    private static File configFile = new File(Launch.minecraftHome, "config/" + MODID + ".cfg");
    private static WatchService watcher;
    
    public static void reloadConfig(ReloadInfo info) {
        try {
            if(configFile.exists() && Files.size(configFile.toPath()) == 0) {
                // Sometimes the watcher fires twice, and the first time the file is empty.
                // I don't know why. This is the workaround.
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        config = new Configuration(configFile, VERSION);
        
        config.load();
        
        boolean needReload = loadFields(config);
        if(info != null) {
            info.needReload = needReload;
        }
        
        config.setCategoryComment("debug", "Note: Some debug features are only available in creative mode or dev environments.");
        
        if(config.hasChanged() || (!Objects.equals(config.getLoadedConfigVersion(), config.getDefinedConfigVersion()))) {
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
            ConfigEnum configEnum = null;
            
            for(Annotation an : field.getAnnotations()) {
                if(an instanceof NeedsReload) {
                    needsReload = (NeedsReload) an;
                } else if(an instanceof ConfigInt) {
                    configInt = (ConfigInt) an;
                } else if(an instanceof ConfigBoolean) {
                    configBoolean = (ConfigBoolean) an;
                } else if(an instanceof ConfigEnum) {
                    configEnum = (ConfigEnum) an;
                }
            }
            
            if(configBoolean == null && configInt == null && configEnum == null) continue;
            
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
            } else if(configEnum != null) {
                boolean lowerCase = true;
                
                Class<? extends Enum> configClass = configEnum.clazz();
                Map<String, ? extends Enum> enumMap = EnumUtils.getEnumMap(configClass);
                String[] valuesStrUpper = (String[])enumMap.keySet().stream().toArray(String[]::new);
                String[] valuesStr = Arrays.stream(valuesStrUpper).map(s -> lowerCase ? s.toLowerCase() : s).toArray(String[]::new);
                
                // allow upgrading boolean to string list
                ConfigCategory cat = config.getCategory(configEnum.cat());
                Property oldProp = cat.get(field.getName());
                String oldVal = null;
                if(oldProp != null && oldProp.getType() != Type.STRING) {
                    oldVal = oldProp.getString();
                    cat.remove(field.getName());
                }
                
                String newValueStr = config.getString(field.getName(), configEnum.cat(),
                        lowerCase ? configEnum.def().toLowerCase() : configEnum.def().toUpperCase(), configEnum.com(), valuesStr);
                if(oldVal != null) {
                    newValueStr = oldVal;
                }
                if(!enumMap.containsKey(newValueStr.toUpperCase())) {
                    newValueStr = configEnum.def().toUpperCase();
                    if(lowerCase) {
                        newValueStr = newValueStr.toLowerCase();
                    }
                }
                newValue = enumMap.get(newValueStr.toUpperCase());
                
                Property newProp = cat.get(field.getName());
                if(!newProp.getString().equals(newValueStr)) {
                    newProp.set(newValueStr);
                }
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
    
    public static List<IConfigElement> getElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        for(Property prop : config.getCategory("render").values()) {
            list.add(new HumanReadableConfigElement(prop));
        }
        return list;
    }
    
    public static void flush() {
        if(config.hasChanged()) {
            config.save();
        }
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
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface ConfigEnum {

        String cat();
        String def();
        String com() default "";
        Class<? extends Enum> clazz();

    }
    
    public static class ReloadInfo {
        public boolean needReload;
    }
    
    public static enum AutomatableBoolean {
        FALSE, TRUE, AUTO
    }
    
}
