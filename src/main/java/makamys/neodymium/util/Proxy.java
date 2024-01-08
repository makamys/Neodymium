package makamys.neodymium.util;

import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public interface Proxy {
    public default void construct(FMLConstructionEvent event) {}
    public default void preInit(FMLPreInitializationEvent event) {}
    public default void init(FMLInitializationEvent event) {}
    public default void postInit(FMLPostInitializationEvent event) {}
    public default void loadComplete(FMLLoadCompleteEvent event) {}
    
    public static class NullProxy implements Proxy {}
}
