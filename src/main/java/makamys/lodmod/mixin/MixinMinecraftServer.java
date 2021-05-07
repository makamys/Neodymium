package makamys.lodmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.lodmod.renderer.MyRenderer;
import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer {
    
    @Shadow
    boolean worldIsBeingDeleted;
    
    @Inject(method = "stopServer", at = @At("HEAD"))
    public void stopServer(CallbackInfo ci) {
        if(!worldIsBeingDeleted) {
            MyRenderer.onStopServer();
        }
    }

    @Inject(method = "updateTimeLightAndEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkSystem;networkTick()V"))
    public void preServerTick(CallbackInfo ci) {
        MyRenderer.serverTick();
    }
    
}
