package makamys.neodymium;

import static makamys.neodymium.Neodymium.LOGGER;

import java.util.List;

import org.lwjgl.opengl.GLContext;

import com.falsepattern.triangulator.api.ToggleableTessellator;
import cpw.mods.fml.common.Loader;
import makamys.neodymium.util.OFUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

public class Compat {
    
    private static boolean wasAdvancedOpenGLEnabled;
    
    public static void applyCompatibilityTweaks() {
        if (Loader.isModLoaded("triangulator")) {
            disableTriangulator();
        }
    }

    private static void disableTriangulator() {
        ((ToggleableTessellator)Tessellator.instance).disableTriangulator();
    }
    
    public static void getCompatibilityWarnings(List<String> warns, List<String> criticalWarns){
        if(Minecraft.getMinecraft().gameSettings.advancedOpengl) {
            warns.add("Advanced OpenGL is enabled, performance may be poor.");
        }
        
        if(Loader.isModLoaded("FastCraft") && !OFUtil.isOptiFinePresent()) {
            criticalWarns.add("FastCraft is present without OptiFine, this is not supported.");
        }
        
        try {
            Class<?> shaders = Class.forName("shadersmod.client.Shaders");
            try {
                String shaderPack = (String)shaders.getMethod("getShaderPackName").invoke(null);
                if(shaderPack != null) {
                    criticalWarns.add("A shader pack is enabled, this is not supported.");
                }
            } catch(Exception e) {
                LOGGER.warn("Failed to get shader pack name");
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            
        }
        
        if(!GLContext.getCapabilities().OpenGL33) {
            criticalWarns.add("OpenGL 3.3 is not supported.");
        }
    }

    public static boolean hasChanged() {
        boolean changed = false;
        
        boolean advGL = Minecraft.getMinecraft().gameSettings.advancedOpengl;
        if(advGL != wasAdvancedOpenGLEnabled) {
            changed = true;
        }
        wasAdvancedOpenGLEnabled = advGL;
        
        return changed;
    }
}
