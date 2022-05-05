package me.cortex.cullmister.textureManager;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;

import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureSubImage2D;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;

public class BindlessSprite {
    public BindlessTexture texture;
    Sprite original;
    public BindlessSprite(Sprite sprite) {
        this.original = sprite;
        //NOTE: Hardcoded mip map level, TODO: make it per texture
        texture = new BindlessTexture(sprite.getWidth(), sprite.getHeight(), 5);
        /*
        int id = glGenTextures();
        TextureUtil.prepareImage(id, 4, sprite.getWidth(), sprite.getHeight());
        texture.id = id;
         */
        //TODO: NEED TO SET THIS BEFORE GETTING ADDRESS/bindless
        /*
        if (maxLevel >= 0) {
            GlStateManager._texParameter(3553, 33085, maxLevel);
            GlStateManager._texParameter(3553, 33082, 0);
            GlStateManager._texParameter(3553, 33083, maxLevel);
            GlStateManager._texParameter(3553, 34049, 0.0f);
        }*/

        glBindTexture(GL_TEXTURE_2D, texture.id);
        GlStateManager._texParameter(3553, 10241, 9986);
        GlStateManager._texParameter(3553, 10240, 9728);
        GlStateManager._pixelStore(3314, 0);

        GlStateManager._pixelStore(3316, 0);
        GlStateManager._pixelStore(3315, 0);

       // GlStateManager._texSubImage2D(3553, level, offsetX, offsetY, width, height, this.format.toGl(), 5121, this.pointer);

        GlStateManager._texParameter(3553, 10242, 33071);
        GlStateManager._texParameter(3553, 10243, 33071);

        //GlStateManager._pixelStore(3317, 4);
        for (int i = 0; i < sprite.images.length; i++) {
            if (sprite.images[i].pointer == 0) continue;
            GlStateManager._pixelStore(3314, sprite.images[i].getWidth());
            //glTexSubImage2D(GL_TEXTURE_2D, i, 0, 0, sprite.images[i].getWidth(), sprite.images[i].getHeight(), GL_RGBA8, GL_UNSIGNED_BYTE, sprite.images[i].pointer);
            //glTextureSubImage2D(texture.id, i, 0, 0, sprite.images[i].getWidth()/2, sprite.images[i].getHeight()/2, GL_RGBA, GL_UNSIGNED_BYTE, sprite.images[i].pointer);
            sprite.images[i].upload(i,0,0,0,0,sprite.getWidth()>>i, sprite.getHeight()>>i, true , false);
        }
    }
}

