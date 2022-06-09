package makamys.neodymium;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.neodymium.renderer.NeoRenderer;
import makamys.neodymium.util.SpriteUtil;
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
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

@Mod(modid = Neodymium.MODID, version = Neodymium.VERSION)
public class Neodymium
{
    public static final String MODID = "neodymium";
    public static final String VERSION = "0.0";
    
    public static final Logger LOGGER = LogManager.getLogger("neodymium");
    
    public static NeoRenderer renderer;
    
    public static boolean fogEventWasPosted;
    
    public static boolean ofFastRender;
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        Config.configFile = event.getSuggestedConfigurationFile();
        Config.reloadConfig();
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    private void onPlayerWorldChanged(World newWorld) {
    	if(getRendererWorld() == null && newWorld != null) {
    		Config.reloadConfig();
    		if(Config.enabled) {
    			SpriteUtil.init();
    		}
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
        if(event.world == getRendererWorld()) {
        	onPlayerWorldChanged(null);
        }
    }
    
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if(!event.world.isRemote) return;
        
        if(isActive()) {
            renderer.onChunkLoad(event);
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
    	if(event.phase == TickEvent.Phase.START) {
    		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    		World world = player != null ? player.worldObj : null;
        	if(world != getRendererWorld()) {
        		onPlayerWorldChanged(world);
        	}
        	
        	if(MixinConfigPlugin.isOptiFinePresent()) {
    	        try {
    	            ofFastRender = (boolean)Class.forName("Config").getMethod("isFastRender").invoke(null);
    	        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
    	                | SecurityException | ClassNotFoundException e) {
    	            // oops
    	        }
        	}
    	}
    }
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if(event.phase == TickEvent.Phase.START) {
            if(isActive()) {
                renderer.serverTick();
            }
        }
    }
    
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if(event.phase == TickEvent.Phase.END) {
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

    
    @SubscribeEvent
    public void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        fogEventWasPosted = true;
    }

    public static boolean shouldRenderVanillaWorld() {
        return !isActive() || (isActive() && renderer.renderWorld && !renderer.rendererActive);
    }

}
