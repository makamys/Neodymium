package makamys.neodymium.util;

import static makamys.neodymium.Constants.LOGGER;

import java.lang.reflect.Field;

import makamys.neodymium.ducks.IMixinGameSettings_OptiFine;
import net.minecraft.client.Minecraft;

public class OFUtil {
    private static boolean isOptiFinePresent, hasCheckedIsOptiFinePresent;
    
    public static boolean isOptiFinePresent() {
        if(!hasCheckedIsOptiFinePresent) {
            checkIfOptiFineIsPresent();
        }
        return isOptiFinePresent;
    }
    
    public static boolean isFogOff() {
        return isOptiFinePresent && getIsFogOff();
    }
    
    private static void checkIfOptiFineIsPresent() {
        try {
            Class<?> optiFineClassTransformerClass = Class.forName("optifine.OptiFineClassTransformer");
            Field instanceField = optiFineClassTransformerClass.getField("instance");
            Object optiFineClassTransformer = instanceField.get(null);
            Field ofZipFileField = optiFineClassTransformerClass.getDeclaredField("ofZipFile");
            ofZipFileField.setAccessible(true);
            Object ofZipFile = ofZipFileField.get(optiFineClassTransformer);
            
            if(ofZipFile != null) {
                LOGGER.debug("OptiFineClassTransformer#ofZipFile is initialized, assuming OptiFine is present");
                isOptiFinePresent = true;
            } else {
                LOGGER.debug("OptiFineClassTransformer#ofZipFile is null, assuming OptiFine is not present");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Couldn't load OptiFineClassTransformer class, assuming OptiFine is not present");
            // no OF
        } catch(Exception e) {
            LOGGER.error("Failed to check if OptiFine is loaded, assuming it isn't. Exception: " + e);
        }
        hasCheckedIsOptiFinePresent = true;
    }

    private static boolean getIsFogOff() {
        return ((IMixinGameSettings_OptiFine)Minecraft.getMinecraft().gameSettings).getOfFogType() == 3;
    }
}
