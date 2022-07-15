package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import makamys.neodymium.ducks.IMixinGameSettings_OptiFine;
import net.minecraft.client.settings.GameSettings;

@Mixin(value = GameSettings.class, remap = false)
@Pseudo
public class MixinGameSettings_OptiFine implements IMixinGameSettings_OptiFine {
    
    @Shadow
    private int ofFogType;
    
    public int getOfFogType() {
        return ofFogType;
    }
    
}
