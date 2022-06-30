package makamys.neodymium.config;

import static makamys.neodymium.Neodymium.MODID;

import cpw.mods.fml.client.config.GuiConfig;
import net.minecraft.client.gui.GuiScreen;

public class NdGuiConfig extends GuiConfig {
    
    public NdGuiConfig(GuiScreen parent) {
        super(parent, Config.getElements(), MODID, MODID, false, false, "Neodymium render settings");
    }
    
}
