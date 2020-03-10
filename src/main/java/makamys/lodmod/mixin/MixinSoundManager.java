package makamys.lodmod.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.audio.SoundManager;


@Mixin(SoundManager.class)
abstract class MixinSoundManager {
    
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        System.out.println("(MIXIN EXAMPLE) Running injector mixin for SoundManager constructor!");
    }
    
    
}
