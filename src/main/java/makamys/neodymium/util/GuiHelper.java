package makamys.neodymium.util;

import lombok.val;
import makamys.neodymium.Compat;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

public class GuiHelper {
    
    public static void begin() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        
        Minecraft mc = Minecraft.getMinecraft();
        
        //GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        //GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, (double)mc.displayWidth, (double)mc.displayHeight, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        //GL11.glLineWidth(1.0F);
        //GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        Tessellator tessellator = Compat.tessellator();
        tessellator.startDrawingQuads();
    }
    
    public static void drawRectangle(int x, int y, int w, int h, int color) {
        val tessellator = Compat.tessellator();
        tessellator.setColorOpaque_I(color);
        tessellator.addVertex(x, y, 0);
        tessellator.addVertex(x, y+h, 0);
        tessellator.addVertex(x+w, y+h, 0);
        tessellator.addVertex(x+w, y, 0);
    }

    
    public static void drawRectangle(int x, int y, int w, int h, int color, int opacity) {
        val tessellator = Compat.tessellator();
        tessellator.setColorRGBA_I(color, opacity);
        tessellator.addVertex(x, y, 0);
        tessellator.addVertex(x, y+h, 0);
        tessellator.addVertex(x+w, y+h, 0);
        tessellator.addVertex(x+w, y, 0);

    }
    
    public static void end() {
        val tessellator = Compat.tessellator();
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);

        //GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    
}
