package makamys.neodymium;

import com.falsepattern.triangulator.api.ToggleableTessellator;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.renderer.Tessellator;

public class Compat {
    public static void applyCompatibilityTweaks() {
        if (Loader.isModLoaded("triangulator")) {
            disableTriangulator();
        }
    }

    private static void disableTriangulator() {
        ((ToggleableTessellator)Tessellator.instance).disableTriangulator();
    }
}
