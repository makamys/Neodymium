package makamys.neodymium;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.mclib.core.MCLib;
import makamys.mclib.core.MCLibModules;
import makamys.neodymium.renderer.NeoRenderer;
import makamys.neodymium.util.OFUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

@Mod(modid = Neodymium.MODID, version = Neodymium.VERSION)
public class Neodymium
{
    public static final String MODID = "neodymium";
    public static final String VERSION = "@VERSION@";
    
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    
    private static final Config.ReloadInfo CONFIG_RELOAD_INFO = new Config.ReloadInfo();
    
    public static NeoRenderer renderer;
    
    public static boolean ofFastRender;
    private static Method ofIsFastRenderMethod;
    
    @EventHandler
    public void preInit(FMLConstructionEvent event) {
        MCLib.init();
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        MCLibModules.updateCheckAPI.submitModTask(MODID, "@UPDATE_URL@");
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event)
    {
        Config.reloadConfig();
    }
    
    private void onPlayerWorldChanged(World newWorld) {
    	if(getRendererWorld() == null && newWorld != null) {
    		Config.reloadConfig();
    	}
    	if(renderer != null) {
            renderer.destroy();
            renderer = null;
        }
    	if(Config.enabled && newWorld != null) {
            renderer = new NeoRenderer(newWorld);
        }
    }
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onWorldUnload(WorldEvent.Unload event) {
        if(!Config.enabled) return;
        
        if(event.world == getRendererWorld()) {
        	onPlayerWorldChanged(null);
        }
    }
    
    public static boolean isActive() {
        return renderer != null && renderer.hasInited && !renderer.destroyPending;
    }
    
    private World getRendererWorld() {
    	return renderer != null ? renderer.world : null;
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if(!Config.enabled) return;
        
    	if(event.phase == TickEvent.Phase.START && isActive()) {
    	    if(Config.hotswap) {
    	        if(Config.reloadIfChanged(CONFIG_RELOAD_INFO)) {
    	            if(CONFIG_RELOAD_INFO.needReload) {
    	                Minecraft.getMinecraft().renderGlobal.loadRenderers();
    	            } else if(renderer != null) {
    	                renderer.reloadShader();
    	            }
    	        }
    	    }
    	}
    }
    
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if(!Config.enabled) return;
        
        if(event.phase == TickEvent.Phase.START) {
            if(OFUtil.isOptiFinePresent()) {
                try {
                    if(ofIsFastRenderMethod == null) {
                        ofIsFastRenderMethod = Class.forName("Config").getMethod("isFastRender");
                    }
                    ofFastRender = (boolean)ofIsFastRenderMethod.invoke(null);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                        | SecurityException | ClassNotFoundException e) {
                    // oops
                }
            }
            
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            World world = player != null ? player.worldObj : null;
            if(world != getRendererWorld()) {
                onPlayerWorldChanged(world);
            }
        } else if(event.phase == TickEvent.Phase.END) {
            if(isActive()) {
                renderer.onRenderTickEnd();
            }
        }
    }
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent event) {
        FontRenderer fontRenderer = RenderManager.instance.getFontRenderer();
        if(isActive() && event.type == ElementType.TEXT && fontRenderer != null && Minecraft.getMinecraft().gameSettings.showDebugInfo && (Config.debugInfoStartY != -1))
        {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int w = scaledresolution.getScaledWidth();
            int h = scaledresolution.getScaledHeight();
            
            int yOffset = 0;
            for(String s : renderer.getDebugText()) {
                fontRenderer.drawStringWithShadow(s, w - fontRenderer.getStringWidth(s) - 10, Config.debugInfoStartY + yOffset, 0xFFFFFF);
                yOffset += 10;
            }
        }
    }

    public static boolean shouldRenderVanillaWorld() {
        return !isActive() || (isActive() && renderer.renderWorld && !renderer.rendererActive);
    }

    public static String modifySplash(String splash) {
        if(splash.equals("OpenGL 1.2!")) {
            return "OpenGL 3.3!";
        } else {
            return splash;
        }
    }

}
