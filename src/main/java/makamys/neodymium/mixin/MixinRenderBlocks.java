package makamys.neodymium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.neodymium.LODMod;
import makamys.neodymium.renderer.FarChunkCache;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

@Mixin(RenderBlocks.class)
abstract class MixinRenderBlocks {
    
    @Redirect(method = "renderBlockLiquid", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z"))
    public boolean shouldSideBeRendered(Block block, IBlockAccess ba, int x, int y, int z, int w) {
        if(LODMod.isActive()) {
            return LODMod.renderer.shouldSideBeRendered(block, ba, x, y, z, w);
        } else {
            return block.shouldSideBeRendered(ba, x, y, z, w);
        }
    }
    
}
