package makamys.neodymium.util;

import static makamys.neodymium.Constants.LOGGER;

import java.util.HashSet;
import java.util.Set;

public class WarningHelper {
    
    private static Set<Integer> shownWarnings = new HashSet<>();
    
    public static void showDebugMessageOnce(String warning) {
        int hash = warning.hashCode();
        if(!shownWarnings.contains(hash)) {
            LOGGER.debug(warning);
            shownWarnings.add(hash);
        }
    }
    
    public static void reset() {
        shownWarnings.clear();
    }
    
}
