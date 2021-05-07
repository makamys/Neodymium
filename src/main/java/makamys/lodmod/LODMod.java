package makamys.lodmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import makamys.lodmod.renderer.MyRenderer;
import makamys.lodmod.util.SpriteUtil;

@Mod(modid = LODMod.MODID, version = LODMod.VERSION)
public class LODMod
{
    public static final String MODID = "lodmod";
    public static final String VERSION = "0.0";
    
    public static final Logger LOGGER = LogManager.getLogger("lodmod");
    
    public static MyRenderer renderer;
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if(!event.world.isRemote) return;
        
        SpriteUtil.init();
        if(renderer != null) {
            LOGGER.warn("Renderer didn't get destroyed last time");
            renderer.destroy();
        }
        renderer = new MyRenderer();
    }
    
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if(!event.world.isRemote) return;
        
        renderer.destroy();
        renderer = null;
    }
    
    public static boolean isActive() {
        return renderer != null;
    }
    
    @SubscribeEvent
    public void onWorldUnload(TickEvent.ServerTickEvent event) {
        if(isActive()) {
            renderer.serverTick();
        }
    }
}
