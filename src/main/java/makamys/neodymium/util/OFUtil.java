package makamys.neodymium.util;

import makamys.neodymium.MixinConfigPlugin;
import makamys.neodymium.ducks.IMixinGameSettings_OptiFine;
import net.minecraft.client.Minecraft;

public class OFUtil {
    private static boolean isOptiFinePresent = MixinConfigPlugin.class.getResource("/optifine/OptiFineTweaker.class") != null;
    
    public static boolean isOptiFinePresent() {
        return isOptiFinePresent;
    }
    
    public static boolean isFogOff() {
        return isOptiFinePresent && getIsFogOff();
    }
    
    private static boolean getIsFogOff() {
        return ((IMixinGameSettings_OptiFine)Minecraft.getMinecraft().gameSettings).getOfFogType() == 3;
    }
}
