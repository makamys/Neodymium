package makamys.neodymium;

import static makamys.neodymium.Constants.LOGGER;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.lwjgl.opengl.GLContext;

import com.falsepattern.triangulator.api.ToggleableTessellator;
import cpw.mods.fml.common.Loader;
import makamys.neodymium.util.OFUtil;
import makamys.neodymium.util.virtualjar.IVirtualJar;
import makamys.neodymium.util.virtualjar.VirtualJar;
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

    public static void forceEnableOptiFineDetectionOfFastCraft() {
        if(Compat.class.getResource("/fastcraft/Tweaker.class") != null) {
            // If OptiFine is present, it's already on the class path at this point, so our virtual jar won't override it.
            LOGGER.info("FastCraft is present, applying hack to forcingly enable FastCraft's OptiFine compat");
            VirtualJar.add(new OptiFineStubVirtualJar());
        }
    }
    
    private static class OptiFineStubVirtualJar implements IVirtualJar {

        @Override
        public String getName() {
            return "optifine-stub";
        }

        @Override
        public InputStream getInputStream(String path) {
            if(path.equals("/optifine/OptiFineForgeTweaker.class")) {
                // Dummy file to make FastCraft think OptiFine is present.
                LOGGER.debug("Returning a dummy /optifine/OptiFineForgeTweaker.class to force FastCraft compat.");
                return new ByteArrayInputStream(new byte[0]);
            } else {
                return null;
            }
        }
        
    }
}
