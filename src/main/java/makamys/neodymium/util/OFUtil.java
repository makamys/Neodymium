package makamys.neodymium.util;

import makamys.neodymium.MixinConfigPlugin;

public class OFUtil {
    private static boolean isOptiFinePresent = MixinConfigPlugin.class.getResource("/optifine/OptiFineTweaker.class") != null;
    
    public static boolean isOptiFinePresent() {
        return isOptiFinePresent;
    }
}
