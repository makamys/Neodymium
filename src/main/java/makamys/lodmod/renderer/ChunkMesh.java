package makamys.lodmod.renderer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

import makamys.lodmod.LODMod;
import makamys.lodmod.ducks.IWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.tileentity.TileEntity;

public class ChunkMesh extends Mesh {
    
    int x;
    int y;
    int z;
    Flags flags;
    
    public static int usedRAM = 0;
    public static int instances = 0;
    
    private ChunkMesh(int x, int y, int z, Flags flags, int quadCount, byte[] data, List<String> stringTable) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.flags = flags;
        this.quadCount = quadCount;
        
        this.buffer = createBuffer(data, stringTable);
        
        usedRAM += buffer.limit();
        instances++;
    }
    
    public ChunkMesh(int x, int y, int z, Flags flags, int quadCount, List<MeshQuad> quads) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.flags = flags;
        this.quadCount = quadCount;
        
        this.buffer = createBuffer(quads);
        
        usedRAM += buffer.limit();
        instances++;
    }
    
    void destroy() {
        usedRAM -= buffer.limit();
        instances--;
    }
    
    private ByteBuffer createBuffer(List<MeshQuad> quads) {
        if(!(flags.hasTexture && flags.hasColor && flags.hasBrightness && !flags.hasNormals)) {
            // for simplicity's sake we just assume this setup
            System.out.println("invalid mesh properties, expected a chunk");
            return null;
        }
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 6 * getStride());
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        ShortBuffer shortBuffer = buffer.asShortBuffer();
        IntBuffer intBuffer = buffer.asIntBuffer();
        
        try {
            for(MeshQuad quad : quads) {
                if(quad.deleted) {
                    continue;
                }
                String spriteName = quad.spriteName;
                
                TextureAtlasSprite tas = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).getAtlasSprite(spriteName);
                
                for (int vertexNum = 0; vertexNum < 6; vertexNum++) {
                    int vi = new int[]{0, 1, 3, 1, 2, 3}[vertexNum];
                    int simpleX = quad.xs[vi];
                    if(simpleX == 255) simpleX = 256;
                    int simpleY = quad.ys[vi];
                    if(simpleY == 255) simpleY = 256;
                    int simpleZ = quad.zs[vi];
                    if(simpleZ == 255) simpleZ = 256;
                    floatBuffer.put(x * 16 + simpleX / 16f); // x
                    floatBuffer.put(y * 16 + simpleY / 16f); // y
                    floatBuffer.put(z * 16 + simpleZ / 16f); // z
                    
                    int relU = quad.relUs[vi];
                    int relV = quad.relVs[vi];
                    
                    floatBuffer.put(tas.getMinU() + (tas.getMaxU() - tas.getMinU()) * (relU / 16f)); // u
                    floatBuffer.put(tas.getMinV() + (tas.getMaxV() - tas.getMinV()) * (relV / 16f)); // v
                    
                    shortBuffer.position(floatBuffer.position() * 2);
                    
                    shortBuffer.put((short)quad.bUs[vi]); // bU
                    shortBuffer.put((short)quad.bVs[vi]); // bV
                    
                    intBuffer.position(shortBuffer.position() / 2);
                    
                    intBuffer.put(quad.cs[vi]); // c
                    
                    floatBuffer.position(intBuffer.position());
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        buffer.position(floatBuffer.position() * 4);
        buffer.flip();
        
        return buffer;
    }

    private ByteBuffer createBuffer(byte[] data, List<String> stringTable) {
        if(!(flags.hasTexture && flags.hasColor && flags.hasBrightness && !flags.hasNormals)) {
            // for simplicity's sake we just assume this setup
            System.out.println("invalid mesh properties, expected a chunk");
            return null;
        }
        int coordsOffset = quadCount * 2;
        int textureOffset = quadCount * (2 + 4 + 4 + 4);
        int brightnessOffset = quadCount * (2 + 4 + 4 + 4 + 4 + 4);
        int colorOffset = quadCount * (2 + 4 + 4 + 4 + 4 + 4 + 4 + 4);
        
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(quadCount * 6 * getStride());
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        ShortBuffer shortBuffer = buffer.asShortBuffer();
        IntBuffer intBuffer = buffer.asIntBuffer();
        
        try {
            for(int quadI = 0; quadI < quadCount; quadI++) {
                short spriteIndex = readShortAt(in, quadI * 2);
                String spriteName = stringTable.get(spriteIndex);
                
                TextureAtlasSprite tas = ((TextureMap)Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).getAtlasSprite(spriteName);
                
                for (int vertexNum = 0; vertexNum < 6; vertexNum++) {
                    int vi = new int[]{0, 1, 3, 1, 2, 3}[vertexNum];
                    int vertexI = 4 * quadI + vi;
                    int offset = vertexI * getStride();
                    int simpleX = Byte.toUnsignedInt(data[coordsOffset + 0 * 4 * quadCount + 4 * quadI + vi]);
                    if(simpleX == 255) simpleX = 256;
                    int simpleY = Byte.toUnsignedInt(data[coordsOffset + 1 * 4 * quadCount + 4 * quadI + vi]);
                    if(simpleY == 255) simpleY = 256;
                    int simpleZ = Byte.toUnsignedInt(data[coordsOffset + 2 * 4 * quadCount + 4 * quadI + vi]);
                    if(simpleZ == 255) simpleZ = 256;
                    floatBuffer.put(x * 16 + simpleX / 16f); // x
                    floatBuffer.put(y * 16 + simpleY / 16f); // y
                    floatBuffer.put(z * 16 + simpleZ / 16f); // z
                    
                    byte relU = data[textureOffset + 0 * 4 * quadCount + 4 * quadI + vi];
                    byte relV = data[textureOffset + 1 * 4 * quadCount + 4 * quadI + vi];
                    
                    floatBuffer.put(tas.getMinU() + (tas.getMaxU() - tas.getMinU()) * (relU / 16f)); // u
                    floatBuffer.put(tas.getMinV() + (tas.getMaxV() - tas.getMinV()) * (relV / 16f)); // v
                    
                    shortBuffer.position(floatBuffer.position() * 2);
                    
                    shortBuffer.put((short)Byte.toUnsignedInt(data[brightnessOffset + 0 * 4 * quadCount + 4 * quadI + vi])); // bU
                    shortBuffer.put((short)Byte.toUnsignedInt(data[brightnessOffset + 1 * 4 * quadCount + 4 * quadI + vi])); // bV
                    
                    intBuffer.position(shortBuffer.position() / 2);
                    
                    intBuffer.put(readIntAt(in, colorOffset + 4 * 4 * quadI + 4 * vi)); // c
                    
                    floatBuffer.position(intBuffer.position());
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        buffer.position(floatBuffer.position() * 4);
        buffer.flip();
        
        return buffer;
    }
    
    // Java is weird.
    public static short readShortAt(DataInputStream in, int offset) {
        try {
            in.reset();
            in.skip(offset);
            return in.readShort();
        } catch(IOException e) {
            return -1;
        }
    }
    
    public static int readIntAt(DataInputStream in, int offset) {
        try {
            in.reset();
            in.skip(offset);
            return in.readInt();
        } catch(IOException e) {
            return -1;
        }
    }
    
    public int getStride() {
        return (3 * 4 + (flags.hasTexture ? 8 : 0) + (flags.hasBrightness ? 4 : 0) + (flags.hasColor ? 4 : 0) + (flags.hasNormals ? 4 : 0));
    }
    
    static ChunkMesh loadChunkMesh(int x, int y, int z) {
        try(DataInputStream in = new DataInputStream(new FileInputStream("tessellator_dump.dat"))){
            List<String> stringTable = Files.readAllLines(Paths.get("tessellator_strings.txt"));
            
            while(true) {
                int xOffset = in.readInt();
                int yOffset = in.readInt();
                int zOffset = in.readInt();
                Flags flags = new Flags(in.readByte());
                
                int quadSize = 2 + 4 * (3 + (flags.hasTexture ? 2 : 0) + (flags.hasBrightness ? 2 : 0) 
                        + (flags.hasColor ? 4 : 0) + (flags.hasNormals ? 4 : 0));
                
                
                int quadCount = in.readInt();
                
                if(xOffset == x && yOffset == y && zOffset == z) {
                    byte[] data = new byte[quadSize * quadCount];
                    in.read(data, 0, data.length);
                    
                    return new ChunkMesh(x, y, z, flags, quadCount, data, stringTable);
                } else {
                    in.skip(quadSize * quadCount);
                }
            }
        } catch(EOFException eof) {
            System.out.println("end??");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // TODO don't repeat yourself
    static List<ChunkMesh> loadAll(){
        List<ChunkMesh> list = new ArrayList<ChunkMesh>();
        try(DataInputStream in = new DataInputStream(new FileInputStream("tessellator_dump.dat"))){
            List<String> stringTable = Files.readAllLines(Paths.get("tessellator_strings.txt"));
            
            while(true) {
                int xOffset = in.readInt();
                int yOffset = in.readInt();
                int zOffset = in.readInt();
                Flags flags = new Flags(in.readByte());
                
                int quadSize = 2 + 4 * (3 + (flags.hasTexture ? 2 : 0) + (flags.hasBrightness ? 2 : 0) 
                        + (flags.hasColor ? 4 : 0) + (flags.hasNormals ? 4 : 0));
                
                
                int quadCount = in.readInt();
                
                if(quadCount > 0) {
                    byte[] data = new byte[quadSize * quadCount];
                    in.read(data, 0, data.length);
                    
                    list.add(new ChunkMesh(xOffset, yOffset, zOffset, flags, quadCount, data, stringTable));
                }
            }
        } catch(EOFException eof) {
            System.out.println("end??");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }
    
    static void saveChunks(List<Integer> coords) {
        System.out.println("saving " + (coords.size() / 3) + " cchunks");
        for(int i = 0; i < coords.size(); i += 3) {
            if(i % 300 == 0) {
                System.out.println((i / 3) + " / " + (coords.size() / 3));
            }
            int theX = coords.get(i);
            int theY = coords.get(i + 1);
            int theZ = coords.get(i + 2);
            
            WorldRenderer wr = new WorldRenderer(Minecraft.getMinecraft().theWorld, new ArrayList<TileEntity>(), theX * 16, theY * 16, theZ * 16, 100000);
    /*
            if (this.occlusionEnabled)
            {
                this.worldRenderers[(var6 * this.renderChunksTall + var5) * this.renderChunksWide + var4].glOcclusionQuery = this.glOcclusionQueryBase.get(var3);
            }*/

            wr.isWaitingOnOcclusionQuery = false;
            wr.isVisible = true;
            wr.isInFrustum = true;
            wr.chunkIndex = 0;
            wr.markDirty();
            wr.updateRenderer(Minecraft.getMinecraft().thePlayer);
        }
        //Tessellator.endSave();
    }
    
    static List<ChunkMesh> getChunkMesh(int theX, int theY, int theZ) {
        WorldRenderer wr = new WorldRenderer(Minecraft.getMinecraft().theWorld, new ArrayList<TileEntity>(), theX * 16, theY * 16, theZ * 16, 100000);
    
        wr.isWaitingOnOcclusionQuery = false;
        wr.isVisible = true;
        wr.isInFrustum = true;
        wr.chunkIndex = 0;
        wr.markDirty();
        wr.updateRenderer(Minecraft.getMinecraft().thePlayer);
        return ((IWorldRenderer)wr).getChunkMeshes();
    }
    
    public static class Flags {
        boolean hasTexture;
        boolean hasBrightness;
        boolean hasColor;
        boolean hasNormals;
        
        public Flags(byte flags) {
            hasTexture = (flags & 1) != 0;
            hasBrightness = (flags & 2) != 0;
            hasColor = (flags & 4) != 0;
            hasNormals = (flags & 8) != 0;
        }
        
        public Flags(boolean hasTexture, boolean hasBrightness, boolean hasColor, boolean hasNormals) {
            this.hasTexture = hasTexture;
            this.hasBrightness = hasBrightness;
            this.hasColor = hasColor;
            this.hasNormals = hasNormals;
        }
        
        public byte toByte() {
            byte flags = 0;
            if(hasTexture) {
                flags |= 1;
            }
            if(hasBrightness) {
                flags |= 2;
            }
            if(hasColor) {
                flags |= 4;
            }
            if(hasNormals) {
                flags |= 8;
            }
            return flags;
        }
    }
    
}

