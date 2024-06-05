package makamys.neodymium.util;

import lombok.val;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {
    
    private static boolean allowResourceOverrides = Boolean.parseBoolean(System.getProperty("neodymium.allowResourceOverrides", "false"));
    
    public static Path getResourcePath(String relPath) {
        if(allowResourceOverrides) {
            File overrideFile = new File(new File(Launch.minecraftHome, "neodymium/resources"), relPath);
            if(overrideFile.exists()) {
                return overrideFile.toPath();
            }
        }
        
        try {
            URL resourceURL = Util.class.getClassLoader().getResource(relPath);
            
            switch(resourceURL.getProtocol()) {
            case "jar":
                String urlString = resourceURL.getPath();
                int lastExclamation = urlString.lastIndexOf('!');
                String newURLString = urlString.substring(0, lastExclamation);
                return FileSystems.newFileSystem(new File(URI.create(newURLString)).toPath(), null).getPath(relPath);
            case "file":
                return new File(URI.create(resourceURL.toString())).toPath();
            default:
                return null;
            }
        } catch(IOException e) {
            return null;
        }
    }
    
    public static String readFile(String path){
        try {
            return new String(Files.readAllBytes(Util.getResourcePath(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    public static byte[] byteBufferToArray(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.limit()];
        int pos = buffer.position();
        buffer.position(0);
        buffer.get(dst);
        buffer.position(pos);
        return dst;
    }
    
    public static int[] intBufferToArray(IntBuffer buffer) {
        int[] dst = new int[buffer.limit()];
        int pos = buffer.position();
        buffer.position(0);
        buffer.get(dst);
        buffer.position(pos);
        return dst;
    }
    
    public static float[] floatBufferToArray(FloatBuffer buffer) {
        float[] dst = new float[buffer.limit()];
        int pos = buffer.position();
        buffer.position(0);
        buffer.get(dst);
        buffer.position(pos);
        return dst;
    }
    
    public static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        val dX = x1 - x2;
        val dY = y1 - y2;
        val dZ = z1 - z2;
        return dX * dX + dY * dY + dZ * dZ;
    }

    public static void dumpTexture() {
        val mc = Minecraft.getMinecraft();
        val workingPath = mc.mcDataDir.toPath();
        val terrainFile = workingPath.resolve("terrain.png").toFile();

        val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        val buf = BufferUtils.createByteBuffer(4 * width * height);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buf);
        try {
            val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            val intBuf = buf.asIntBuffer();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    img.setRGB(x, y, intBuf.get());
                }
            }
            ImageIO.write(img, "png", terrainFile);
        } catch (IOException e) {
            ChatUtil.showNeoChatMessage("Failed to dump terrain texture", ChatUtil.MessageVerbosity.ERROR);
            e.printStackTrace();
        }
        ChatUtil.showChatMessage("Dumped terrain texture to: " + terrainFile.getAbsolutePath());
    }
    
    public static int createBrightness(int sky, int block) {
        return sky << 20 | block << 4;
    }
    
    public static void setPositionAndLimit(Buffer buffer, int position, int limit) {
        buffer.position(position);
        buffer.limit(limit);
    }
}
