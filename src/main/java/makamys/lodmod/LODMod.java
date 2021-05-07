package makamys.lodmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
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
import makamys.lodmod.renderer.LODRenderer;
import makamys.lodmod.util.SpriteUtil;

@Mod(modid = LODMod.MODID, version = LODMod.VERSION)
public class LODMod
{
    public static final String MODID = "lodmod";
    public static final String VERSION = "0.0";
    
    public static final Logger LOGGER = LogManager.getLogger("lodmod");
    
    public static LODRenderer renderer;
    
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
        renderer = new LODRenderer();
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
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent event) {
        FontRenderer fontRenderer = RenderManager.instance.getFontRenderer();
        if(isActive() && event.type == ElementType.TEXT && fontRenderer != null && Minecraft.getMinecraft().gameSettings.showDebugInfo)
        {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int w = scaledresolution.getScaledWidth();
            int h = scaledresolution.getScaledHeight();
            String s = renderer.getDebugText();
            fontRenderer.drawStringWithShadow(s, w - fontRenderer.getStringWidth(s) - 10, 80, 0xFFFFFF);
        }
    }
}
