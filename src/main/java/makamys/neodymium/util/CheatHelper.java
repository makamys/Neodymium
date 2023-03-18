package makamys.neodymium.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.world.World;

public class CheatHelper {
    
    private static final boolean IS_DEV_ENVIRONMENT = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    
    public static boolean canCheat() {
        if(IS_DEV_ENVIRONMENT) {
            return true;
        } else {
            return isCreative(Minecraft.getMinecraft().thePlayer);
        }
    }

    public static boolean isCreative(EntityPlayer player) {
        return player != null && player.capabilities.isCreativeMode;
    }

    public static boolean isCreativeByName(String player) {
        World world = Minecraft.getMinecraft().theWorld;
        if(world != null) {
            return isCreative(world.getPlayerEntityByName(player));
        }
        return false;
    }
    
}
