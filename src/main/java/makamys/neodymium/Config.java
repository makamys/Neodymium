package makamys.neodymium;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.lwjgl.input.Keyboard;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enabled;
    public static int chunkLoadsPerTick;
    public static List<Class<?>> blockClassBlacklist;
    public static double fogStart;
    public static double fogEnd;
    public static double farPlaneDistanceMultiplier;
    public static float maxSimpleMeshHeight;
    public static boolean forceVanillaBiomeTemperature;
    public static boolean hideUnderVanillaChunks;
    public static boolean disableChunkMeshes;
    public static boolean disableSimpleMeshes;
    public static boolean saveMeshes;
    public static boolean optimizeChunkMeshes;
    public static int maxMeshesPerFrame;
    public static int sortFrequency;
    public static int gcRate;
    public static int VRAMSize;
    public static int debugPrefix;
    public static int debugInfoStartY;
    
    static File configFile;
    
    public static void reloadConfig() {
        Configuration config = new Configuration(configFile);
        
        config.load();
        enabled = config.get("General", "enabled", true).getBoolean();
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
        
        maxSimpleMeshHeight = (float)config.get("Debug", "maxSimpleMeshHeight", 1000.0).getDouble();
        
        forceVanillaBiomeTemperature = config.get("Simple mesh generation", "forceVanillaBiomeTemperature", true).getBoolean();
        
        hideUnderVanillaChunks = config.getBoolean("hideUnderVanillaChunks", "render", true, "");
        disableChunkMeshes = config.getBoolean("disableChunkMeshes", "render", true, "");
        disableSimpleMeshes = config.getBoolean("disableSimpleMeshes", "render", false, "");
        optimizeChunkMeshes = config.getBoolean("optimizeChunkMeshes", "render", true, "");
        saveMeshes = config.getBoolean("saveMeshes", "render", false, "");
        maxMeshesPerFrame = config.getInt("maxMeshesPerFrame", "render", -1, -1, Integer.MAX_VALUE, "");
        sortFrequency = config.getInt("sortFrequency", "render", 1, 1, Integer.MAX_VALUE, "");
        gcRate = config.getInt("gcRate", "render", 1, 1, Integer.MAX_VALUE, "Maximum number of meshes to relocate each frame.");
        VRAMSize = config.getInt("VRAMSize", "render", 1024, 1, Integer.MAX_VALUE, "VRAM buffer size (MB).");
        enableFog = config.getBoolean("enableFog", "render", true, "");
        debugPrefix = config.getInt("debugPrefix", "debug", Keyboard.KEY_F4, -1, Integer.MAX_VALUE, "This key has to be held down while pressing the debug keybinds. LWJGL keycode. Setting this to 0 will make the keybinds usable without holding anything else down. Setting this to -1 will disable debug keybinds entirely.");
        debugInfoStartY = config.getInt("debugInfoStartY", "debug", 80, -1, Integer.MAX_VALUE, "The Y position of the first line of the debug info in the F3 overlay. Set this to -1 to disable showing that info.");
        
        if(config.hasChanged()) {
            config.save();
        }
    }

    public static boolean enableFog;
    
}
