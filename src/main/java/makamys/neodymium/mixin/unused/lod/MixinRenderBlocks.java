package makamys.neodymium.mixin.unused.lod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makamys.neodymium.Neodymium;
import makamys.neodymium.renderer.lod.FarChunkCache;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Unused remnant from LODMod. Disables a wall being drawn on the edges of chunks facing unloaded chunks. */
@Mixin(RenderBlocks.class)
abstract class MixinRenderBlocks {
    
    @Redirect(method = "renderBlockLiquid", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z"))
    public boolean shouldSideBeRendered(Block block, IBlockAccess ba, int x, int y, int z, int w) {
        if(Neodymium.isActive()) {
            return Neodymium.renderer.shouldSideBeRendered(block, ba, x, y, z, w);
        } else {
            return block.shouldSideBeRendered(ba, x, y, z, w);
        }
    }
    
}
