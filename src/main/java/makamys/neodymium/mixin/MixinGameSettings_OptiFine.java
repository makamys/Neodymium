package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import makamys.neodymium.ducks.IMixinGameSettings_OptiFine;
import net.minecraft.client.settings.GameSettings;

@Mixin(value = GameSettings.class,
       remap = false)
@Pseudo
public abstract class MixinGameSettings_OptiFine implements IMixinGameSettings_OptiFine {
    @Dynamic
    @Shadow
    private int ofFogType;
    
    public int nd$getOfFogType() {
        return ofFogType;
    }
}
