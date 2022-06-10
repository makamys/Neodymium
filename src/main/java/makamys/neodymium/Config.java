package makamys.neodymium;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static makamys.neodymium.Neodymium.LOGGER;
import static makamys.neodymium.Neodymium.MODID;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.lwjgl.input.Keyboard;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enabled;
    public static boolean hotswap;
    
    public static boolean simplifyChunkMeshes;
    public static int maxMeshesPerFrame;
    public static int sortFrequency;
    public static int gcRate;
    public static int VRAMSize;
    public static int debugPrefix;
    public static int debugInfoStartY;
    public static boolean renderFog;
    
    // Unused LOD stuff
    public static int chunkLoadsPerTick = 64;
    public static List<Class<?>> blockClassBlacklist = Arrays.asList();
    public static double fogStart = 0.25f;
    public static double fogEnd = 1f;
    public static double farPlaneDistanceMultiplier = 1;
    public static boolean forceVanillaBiomeTemperature = false;
    public static boolean hideUnderVanillaChunks = false;
    public static boolean disableChunkMeshes = false;
    public static boolean disableSimpleMeshes = true;
    public static boolean saveMeshes = false;
    
    private static File configFile = new File(Launch.minecraftHome, "config/" + MODID + ".cfg");
    private static WatchService watcher;
    
    public static void reloadConfig() {
        try {
            if(Files.size(configFile.toPath()) == 0) {
                // Sometimes the watcher fires twice, and the first time the file is empty.
                // I don't know why. This is the workaround.
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        Configuration config = new Configuration(configFile);
        
        config.load();
        enabled = config.getBoolean("enabled", "_general", true, "Set this to false to fully disable the mod.");
        hotswap = config.getBoolean("hotswap", "_general", false, "Apply changes made in the config file immediately without having to reload the world. Off by default because it could potentially cause poor performance on certain platforms. Note that not all settings can be hotswapped.");
        
        simplifyChunkMeshes = config.getBoolean("simplifyChunkMeshes", "render", false, "Simplify chunk meshes so they are made of less vertices. Proof of concept, produces very janky results.");
        
        sortFrequency = config.getInt("sortFrequency", "render", 1, 1, Integer.MAX_VALUE, "Interval (in frames) between the sorting of meshes. Increasing this might increase framerate, but increase the likelyhood of graphical artifacts when moving quickly.");
        gcRate = config.getInt("gcRate", "render", 1, 1, Integer.MAX_VALUE, "Maximum number of meshes to relocate in the buffer each frame. Setting this to a higher value will make it harder for the VRAM to get full (which causes a lag spike when it happens), but slightly reduces overall framerate. Examining the VRAM debugger can help find the right value.");
        VRAMSize = config.getInt("VRAMSize", "render", 1024, 1, Integer.MAX_VALUE, "VRAM buffer size (MB). 512 seems to be a good value on Normal render distance. Increase this if you encounter warnings about the VRAM getting full. Does not affect RAM usage.");
        renderFog = config.getBoolean("renderFog", "render", true, "Render frog? Disabling this might increase framerate.");
        
        maxMeshesPerFrame = config.getInt("maxMeshesPerFrame", "debug", -1, -1, Integer.MAX_VALUE, "");
        debugPrefix = config.getInt("debugPrefix", "debug", Keyboard.KEY_F4, -1, Integer.MAX_VALUE, "This key has to be held down while pressing the debug keybinds. LWJGL keycode. Setting this to 0 will make the keybinds usable without holding anything else down. Setting this to -1 will disable debug keybinds entirely.");
        debugInfoStartY = config.getInt("debugInfoStartY", "debug", 80, -1, Integer.MAX_VALUE, "The Y position of the first line of the debug info in the F3 overlay. Set this to -1 to disable showing that info.");
        
        if(config.hasChanged()) {
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
    
    // Unused
    public static void loadConfigLOD(Configuration config) {
        chunkLoadsPerTick = config.get("General", "chunkLoadsPerTick", 64).getInt();
        blockClassBlacklist = Arrays.stream(config.get("General", "blockClassBlacklist", "net.minecraft.block.BlockRotatedPillar;biomesoplenty.common.blocks.BlockBOPLog;gregapi.block.multitileentity.MultiTileEntityBlock").getString().split(";"))
                .map(className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        fogStart = config.get("Fog", "fogStart", "0.4").getDouble();
        fogEnd = config.get("Fog", "fogEnd", "0.8").getDouble();
        farPlaneDistanceMultiplier = config.get("Fog", "farPlaneDistanceMultiplier", "1.0").getDouble();
        forceVanillaBiomeTemperature = config.get("Simple mesh generation", "forceVanillaBiomeTemperature", true).getBoolean();
        hideUnderVanillaChunks = config.getBoolean("hideUnderVanillaChunks", "render", true, "");
        disableChunkMeshes = config.getBoolean("disableChunkMeshes", "render", true, "");
        disableSimpleMeshes = config.getBoolean("disableSimpleMeshes", "render", false, "");
        saveMeshes = config.getBoolean("saveMeshes", "render", false, "");
    }
    
    public static boolean reloadIfChanged() {
        boolean reloaded = false;
        if(watcher != null) {
            WatchKey key = watcher.poll();
            
            if(key != null) {
                for(WatchEvent<?> event: key.pollEvents()) {
                    if(event.context().toString().equals(configFile.getName())) {
                        reloadConfig();
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
    
}
