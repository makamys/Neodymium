package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.neodymium.Neodymium;
import net.minecraft.client.gui.GuiMainMenu;

@Mixin(GuiMainMenu.class)
abstract class MixinGuiMainMenu {
    
    @Shadow
    private String splashText;
    
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void postConstructor(CallbackInfo ci) {
        splashText = Neodymium.modifySplash(splashText);
    }
    
}
