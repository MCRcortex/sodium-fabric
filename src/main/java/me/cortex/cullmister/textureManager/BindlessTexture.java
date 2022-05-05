package me.cortex.cullmister.textureManager;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureStorage2D;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.NVBindlessTexture.*;

public class BindlessTexture {
    public int id;
    public long addr;
    public BindlessTexture(int width, int height, int levels) {
        this(width, height, levels, GL_RGBA8);
    }
    //TODO: need to add sampler option for the addr
    // TODO: NEED TO ADD LIKE A THING TO REGET AN ADDRESS OR SOMETHING with config
    public BindlessTexture(int width, int height, int levels, int format) {
        id = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(id, levels, format, width, height);
        /*
        addr = glGetTextureHandleNV(id);
        if (addr == 0) {
            throw new IllegalStateException();
        }
        glMakeTextureHandleResidentNV(addr);

         */
    }

    public void delete() {
        if (addr != 0)
            glMakeTextureHandleNonResidentNV(addr);
        glDeleteTextures(id);
        id = 0;
        addr = 0;
    }
}
