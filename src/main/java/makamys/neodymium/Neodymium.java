package makamys.neodymium;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
import makamys.neodymium.util.ChatUtil;
import makamys.neodymium.util.OFUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
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

    private boolean renderDebugText = false;
    
    public static NeoRenderer renderer;
    
    private static World rendererWorld;
    
    @EventHandler
    public void preInit(FMLConstructionEvent event) {
        MCLib.init();
        Compat.applyCompatibilityTweaks();
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
        ChatUtil.resetShownChatMessages();
    }
    
    private void onPlayerWorldChanged(World newWorld) {
    	if(getRendererWorld() == null && newWorld != null) {
    		Config.reloadConfig();
    	}
    	if(renderer != null) {
            destroyRenderer();
        }
    	if(Config.enabled && newWorld != null) {
    	    List<String> warns = new ArrayList<>();
    	    List<String> criticalWarns = new ArrayList<>();
    	    
    	    Compat.getCompatibilityWarnings(warns, criticalWarns);
    	    
    	    if(!criticalWarns.isEmpty()) {
    	        criticalWarns.add("Neodymium has been disabled due to a critical incompatibility.");
    	    }
    	    
    	    if(!Config.ignoreIncompatibilities) {
        	    for(String warn : warns) {
        	        ChatUtil.showNeoChatMessage(warn, ChatUtil.MessageVerbosity.WARNING, true);
        	    }
        	    for(String fatalWarn : criticalWarns) {
                    ChatUtil.showNeoChatMessage(fatalWarn, ChatUtil.MessageVerbosity.ERROR, true);
                }
    	    }
    	    
    	    for(String warn : warns) {
                LOGGER.warn(warn);
            }
            for(String criticalWarn : criticalWarns) {
                LOGGER.warn("Critical: " + criticalWarn);
            }
            
    	    if(criticalWarns.isEmpty() || Config.ignoreIncompatibilities) {
    	        renderer = new NeoRenderer(newWorld);
    	        renderer.hasIncompatibilities = !warns.isEmpty() || !criticalWarns.isEmpty();
    	    }
        }
    	rendererWorld = newWorld;
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
    	return rendererWorld;
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
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            World world = player != null ? player.worldObj : null;
            if(world != getRendererWorld()) {
                onPlayerWorldChanged(world);
            }
            
            if(isActive()) {
                renderer.forceRenderFog = true;
            }
        } else if(event.phase == TickEvent.Phase.END) {
            if(isActive()) {
                renderer.onRenderTickEnd();
            }
        }
    }
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (Config.showDebugInfo && isActive()) {
            if (event.type.equals(RenderGameOverlayEvent.ElementType.DEBUG)) {
                renderDebugText = true;
            } else if (renderDebugText && (event instanceof RenderGameOverlayEvent.Text) && event.type.equals(RenderGameOverlayEvent.ElementType.TEXT)) {
                renderDebugText = false;
                RenderGameOverlayEvent.Text text = (RenderGameOverlayEvent.Text) event;
                text.right.add(null);
                text.right.addAll(renderer.getDebugText());
            }
        }
    }
    
    @SubscribeEvent
    public void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        if(isActive()) {
            renderer.forceRenderFog = false;
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

    public static void destroyRenderer() {
        renderer.destroy();
        renderer = null;
        rendererWorld = null;
    }

}
