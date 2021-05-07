package makamys.lodmod.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import makamys.lodmod.ducks.ITessellator;
import makamys.lodmod.renderer.ChunkMesh;
import makamys.lodmod.renderer.MeshQuad;
import makamys.lodmod.renderer.MeshQuad.QuadPlaneComparator;
import net.minecraft.client.renderer.Tessellator;

@Mixin(Tessellator.class)
abstract class MixinTessellator implements ITessellator {
    
    @Shadow
    private int vertexCount;
    
    @Shadow
    private int[] rawBuffer;
    
    @Shadow
    private double xOffset;
    @Shadow
    private double yOffset;
    @Shadow
    private double zOffset;
    
    @Shadow
    private boolean hasTexture;
    @Shadow
    private boolean hasBrightness;
    @Shadow
    private boolean hasColor;
    @Shadow
    private boolean hasNormals;
    
    private static int totalOriginalQuadCount = 0;
    private static int totalSimplifiedQuadCount = 0;
    /*
    public static void endSave() {
        if(out == null) {
            return;
        }
        try {
            out.close();
            String nameList = String.join("\n", ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).mapUploadedSprites.keySet());
            Files.write(nameList, new File("tessellator_strings.txt"), Charset.forName("UTF-8"));
            out = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
    
    public ChunkMesh toChunkMesh() {
        if(this.vertexCount % 4 != 0) {
            System.out.println("Error: Vertex count is not a multiple of 4");
            return null;
        }
        
        List<MeshQuad> quads = new ArrayList<>();
        
        List<Integer> spriteIndexes = new ArrayList<>();
        List<Byte> xs = new ArrayList<>();
        List<Byte> ys = new ArrayList<>();
        List<Byte> zs = new ArrayList<>();
        List<Byte> relUs = new ArrayList<>();
        List<Byte> relVs = new ArrayList<>();
        List<Byte> bUs = new ArrayList<>();
        List<Byte> bVs = new ArrayList<>();
        List<Integer> cs = new ArrayList<>();
        
        for(int quadI = 0; quadI < this.vertexCount / 4; quadI++) {
            MeshQuad quad = new MeshQuad(rawBuffer, quadI * 32, new ChunkMesh.Flags(hasTexture, hasBrightness, hasColor, hasNormals));
            /*if(quad.bUs[0] == quad.bUs[1] && quad.bUs[1] == quad.bUs[2] && quad.bUs[2] == quad.bUs[3] && quad.bUs[3] == quad.bVs[0] && quad.bVs[0] == quad.bVs[1] && quad.bVs[1] == quad.bVs[2] && quad.bVs[2] == quad.bVs[3] && quad.bVs[3] == 0) {
                quad.deleted = true;
            }*/
            if(quad.plane == quad.PLANE_XZ && !quad.isClockwiseXZ()) {
                // water hack
                quad.deleted = true;
            }
            quads.add(quad);
        }
        boolean optimize = true;
        if(optimize) {
            ArrayList<ArrayList<MeshQuad>> quadsByPlaneDir = new ArrayList<>(); // XY, XZ, YZ
            for(int i = 0; i < 3; i++) {
                quadsByPlaneDir.add(new ArrayList<MeshQuad>());
            }
            for(MeshQuad quad : quads) {
                if(quad.plane != MeshQuad.PLANE_NONE) {
                    quadsByPlaneDir.get(quad.plane).add(quad);
                }
            }
            for(int plane = 0; plane < 3; plane++) {
                quadsByPlaneDir.get(plane).sort(MeshQuad.QuadPlaneComparator.quadPlaneComparators[plane]);
            }
            
            for(int plane = 0; plane < 3; plane++) {
                List<MeshQuad> planeDirQuads = quadsByPlaneDir.get(plane);
                int planeStart = 0;
                for(int quadI = 0; quadI < planeDirQuads.size(); quadI++) {
                    MeshQuad quad = planeDirQuads.get(quadI);
                    MeshQuad nextQuad = quadI == planeDirQuads.size() - 1 ? null : planeDirQuads.get(quadI + 1);
                    if(!quad.onSamePlaneAs(nextQuad)) {
                        simplifyPlane(planeDirQuads.subList(planeStart, quadI));
                        planeStart = quadI + 1;
                    }
                }
            }
        }
        
        int quadCount = countValidQuads(quads);
        
        totalOriginalQuadCount += quads.size();
        totalSimplifiedQuadCount += quadCount;
        //System.out.println("simplified quads " + totalOriginalQuadCount + " -> " + totalSimplifiedQuadCount + " (ratio: " + ((float)totalSimplifiedQuadCount / (float)totalOriginalQuadCount) + ") totalMergeCountByPlane: " + Arrays.toString(totalMergeCountByPlane));
        
        if(quadCount > 0) {
            return new ChunkMesh(
                    (int)(-xOffset / 16), (int)(-yOffset / 16), (int)(-zOffset / 16),
                    new ChunkMesh.Flags(hasTexture, hasBrightness, hasColor, hasNormals),
                    quadCount, quads);
        } else {
            return null;
        }
    }
    
    private void simplifyPlane(List<MeshQuad> planeQuads) {
        MeshQuad lastQuad = null;
        // Pass 1: merge quads to create rows
        for(MeshQuad quad : planeQuads) {
            if(lastQuad != null) {
                lastQuad.tryToMerge(quad);
            }
            if(quad.isValid(quad)) {
                lastQuad = quad;
            }
        }
        
        // Pass 2: merge rows to create rectangles
        // TODO optimize?
        for(int i = 0; i < planeQuads.size(); i++) {
            for(int j = i + 1; j < planeQuads.size(); j++) {
                planeQuads.get(i).tryToMerge(planeQuads.get(j));
            }
        }
    }
    
    private int countValidQuads(List<MeshQuad> quads) {
        int quadCount = 0;
        for(MeshQuad quad : quads) {
            if(!quad.deleted) {
                quadCount++;
            }
        }
        return quadCount;
    }
}
