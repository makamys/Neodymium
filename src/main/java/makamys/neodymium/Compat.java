package makamys.neodymium;

import com.falsepattern.triangulator.api.ToggleableTessellator;
import cpw.mods.fml.common.Loader;
import makamys.neodymium.config.Config;
import makamys.neodymium.util.virtualjar.IVirtualJar;
import makamys.neodymium.util.virtualjar.VirtualJar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static makamys.neodymium.Constants.LOGGER;

public class Compat {
    
    private static boolean isGL33Supported;
    
    private static boolean wasAdvancedOpenGLEnabled;
    
    private static int notEnoughVRAMAmountMB = -1;

    private static boolean IS_RPLE_PRESENT;

    private static boolean IS_FALSE_TWEAKS_PRESENT;
    
    private static boolean IS_HODGEPODGE_SPEEDUP_ANIMATIONS_ENABLED;
    private static boolean IS_ANGELICA_SPEEDUP_ANIMATIONS_ENABLED;

    private static boolean isShadersEnabled;
    
    public static void init() {
        isGL33Supported = GLContext.getCapabilities().OpenGL33;
        
        if (Loader.isModLoaded("triangulator")) {
            disableTriangulator();
        }

        if (Loader.isModLoaded("rple")) {
            IS_RPLE_PRESENT = true;
        }

        if (Loader.isModLoaded("falsetweaks")) {
            IS_FALSE_TWEAKS_PRESENT = true;
        }
        
        IS_HODGEPODGE_SPEEDUP_ANIMATIONS_ENABLED = checkIfHodgepodgeSpeedupAnimationsIsEnabled();
        IS_ANGELICA_SPEEDUP_ANIMATIONS_ENABLED = checkIfAngelicaSpeedupAnimationsIsEnabled();
    }
    
    public static boolean enableVanillaChunkMeshes() {
        return Config.enableVanillaChunkMeshes && !isFalseTweaksModPresent();
    }
    
    public static boolean keepRenderListLogic() {
        return enableVanillaChunkMeshes() || Constants.KEEP_RENDER_LIST_LOGIC;
    }

    private static boolean checkIfHodgepodgeSpeedupAnimationsIsEnabled() {
        Boolean result = null;
        if (Loader.isModLoaded("hodgepodge")) {
            try {
                Class<?> FixesConfigCls = Class.forName("com.mitchej123.hodgepodge.config.FixesConfig");
                Boolean speedupAnimations = (Boolean)FixesConfigCls.getField("speedupAnimations").get(null);
                result = speedupAnimations;
            } catch(Exception e) {
                LOGGER.debug("Failed to determine if Hodgepodge's speedupAnimations is enabled using new config class, trying old one.", e);
            }
            if(result == null) {
                try {
                    Class<?> CommonCls = Class.forName("com.mitchej123.hodgepodge.Common");
                    Object config = CommonCls.getField("config").get(null);
                    Class<?> configCls = config.getClass();
                    boolean speedupAnimations = (Boolean)configCls.getField("speedupAnimations").get(config);
                    result = speedupAnimations;
                } catch(Exception e) {
                    LOGGER.debug("Failed to determine if Hodgepodge's speedupAnimations is enabled using old config class.", e);
                }
            }
            if(result != null) {
                LOGGER.debug("Hodgepodge's speedupAnimations is set to " + result);
            } else {
                LOGGER.warn("Failed to determine if Hodgepodge's speedupAnimations is enabled, assuming false");
                result = false;
            }
        } else {
            LOGGER.debug("Hodgepodge is missing, treating speedupAnimations as false");
        }
        LOGGER.debug("Compat fix will " + (result ? "" : "not ") + "be enabled");
        return result;
    }
    
    private static boolean checkIfAngelicaSpeedupAnimationsIsEnabled() {
        Boolean result = null;
        if (Loader.isModLoaded("angelica")) {
            try {
                Class<?> AngelicaConfigCls = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig");
                Boolean speedupAnimations = (Boolean)AngelicaConfigCls.getField("speedupAnimations").get(null);
                result = speedupAnimations;
            } catch(Exception e) {
                LOGGER.debug("Failed to determine if Angelica's speedupAnimations is enabled.", e);
            }
            if(result != null) {
                LOGGER.debug("Angelica's speedupAnimations is set to " + result);
            } else {
                LOGGER.warn("Failed to determine if Angelica's speedupAnimations is enabled, assuming false");
                result = false;
            }
        } else {
            LOGGER.debug("Angelica is missing, treating speedupAnimations as false");
        }
        LOGGER.debug("Compat fix will " + (result ? "" : "not ") + "be enabled");
        return result;
    }

    public static boolean isRPLEModPresent() {
        return IS_RPLE_PRESENT;
    }

    public static boolean isFalseTweaksModPresent() {
        return IS_FALSE_TWEAKS_PRESENT;
    }
    
    public static boolean isSpeedupAnimationsEnabled() {
        return IS_HODGEPODGE_SPEEDUP_ANIMATIONS_ENABLED || IS_ANGELICA_SPEEDUP_ANIMATIONS_ENABLED;
    }

    public static boolean isOptiFineShadersEnabled() {
        return isShadersEnabled;
    }

    public static void updateOptiFineShadersState() {
        try {
            Class<?> shaders = Class.forName("shadersmod.client.Shaders");
            try {
                String shaderPack = (String)shaders.getMethod("getShaderPackName").invoke(null);
                if(shaderPack != null) {
                    isShadersEnabled = true;
                    return;
                }
            } catch(Exception e) {
                LOGGER.warn("Failed to get shader pack name");
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {

        }
        isShadersEnabled = false;
    }

    private static void disableTriangulator() {
        ((ToggleableTessellator)Tessellator.instance).disableTriangulator();
    }
    
    public static void getCompatibilityWarnings(List<Warning> warns, List<Warning> criticalWarns, boolean statusCommand){
        if(Minecraft.getMinecraft().gameSettings.advancedOpengl) {
            warns.add(new Warning("Advanced OpenGL is enabled, performance may be poor." + (statusCommand ? " Click here to disable it." : "")).chatAction("neodymium disable_advanced_opengl"));
        }
        
        if(!isGL33Supported) {
            criticalWarns.add(new Warning("OpenGL 3.3 is not supported."));
        }
        if(detectedNotEnoughVRAM()) {
            criticalWarns.add(new Warning("Not enough VRAM"));
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
    
    public static void onNotEnoughVRAM(int amountMB) {
        notEnoughVRAMAmountMB = amountMB;
    }
    
    public static void reset() {
        notEnoughVRAMAmountMB = -1;
    }
    
    private static boolean detectedNotEnoughVRAM() {
        return Config.VRAMSize == notEnoughVRAMAmountMB;
    }

    public static void forceEnableOptiFineDetectionOfFastCraft() {
        if(Compat.class.getResource("/fastcraft/Tweaker.class") != null) {
            // If OptiFine is present, it's already on the class path at this point, so our virtual jar won't override it.
            LOGGER.info("FastCraft is present, applying hack to forcingly enable FastCraft's OptiFine compat");
            VirtualJar.add(new OptiFineStubVirtualJar());
        }
    }
    
    public static boolean disableAdvancedOpenGL() {
        GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;
        
        if(gameSettings.advancedOpengl) {
            gameSettings.advancedOpengl = false;
            gameSettings.saveOptions();
            return true;
        }
        return false;
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
    
    public static class Warning {
        public String text;
        public String chatAction;
        
        public Warning(String text) {
            this.text = text;
        }
        
        public Warning chatAction(String command) {
            this.chatAction = command;
            return this;
        }
    }
}
