package makamys.lodmod.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.world.ChunkCoordIntPair;

public class SpriteUtil {
    
    private static int[] spriteIndexMap;
    public static List<TextureAtlasSprite> sprites;
    
    private static Map<Long, Integer> uv2spriteIndex = new HashMap<>();
    
    private static int findSpriteIndexForUV(float u, float v) {
        Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
        
        int spriteIndex = 0;
        for(TextureAtlasSprite tas : uploadedSprites.values()) {
            if(tas.getMinU() <= u && u <= tas.getMaxU() && tas.getMinV() <= v && v <= tas.getMaxV()) {
                break;
            }
            spriteIndex++;
        }
        return spriteIndex;
    }
    
    public static int getSpriteIndexForUV(float u, float v){
        long key = ChunkCoordIntPair.chunkXZ2Int((int)(u * Integer.MAX_VALUE), (int)(v * Integer.MAX_VALUE));
        int index = uv2spriteIndex.getOrDefault(key, -1);
        if(index == -1) {
            index = findSpriteIndexForUV(u, v);
            uv2spriteIndex.put(key, index);
        }
        return index;
    }
    
    public static TextureAtlasSprite getSprite(int i){
        if(i >= 0 && i < sprites.size()) {
            return sprites.get(i);
        } else {
            return null;
        }
    }
    
    public static void init() {
        Map<String, TextureAtlasSprite> uploadedSprites = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites;
        sprites = uploadedSprites.values().stream().collect(Collectors.toList());
    }
}
