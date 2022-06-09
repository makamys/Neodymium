package makamys.neodymium.util;

import net.minecraft.world.biome.BiomeGenBase;

public class MCUtil {
    
    public static float getBiomeTemperatureVanilla(BiomeGenBase biome, int p_150564_1_, int p_150564_2_, int p_150564_3_){    
        if (p_150564_2_ > 64)
        {
            float f = (float)BiomeGenBase.temperatureNoise
                    .func_151601_a((double)p_150564_1_ * 1.0D / 8.0D, (double)p_150564_3_ * 1.0D / 8.0D) * 4.0F;
            return biome.temperature - (f + (float)p_150564_2_ - 64.0F) * 0.05F / 30.0F;
        }
        else
        {
            return biome.temperature;
        }
    }
    
}
