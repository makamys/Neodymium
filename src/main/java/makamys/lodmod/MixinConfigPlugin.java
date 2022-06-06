package makamys.lodmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinConfigPlugin implements IMixinConfigPlugin {
    
    private static boolean isOptiFinePresent = MixinConfigPlugin.class.getResource("/optifine/OptiFineTweaker.class") != null;
    
    @Override
    public void onLoad(String mixinPackage) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getRefMapperConfig() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        mixins.addAll(Arrays.asList("MixinChunkCache",
        "MixinEntityRenderer",
        "MixinRenderGlobal",
        "MixinWorldRenderer",
        "MixinRenderBlocks"));
        
        if (isOptiFinePresent()) {
            System.out.println("Detected OptiFine");
            mixins.add("MixinRenderGlobal_OptiFine");
        }
        
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // TODO Auto-generated method stub
        
    }
    
    public static boolean isOptiFinePresent() {
        return isOptiFinePresent;
    }
    
}
