package me.cortex.cullmister.textures;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.texture.Sprite;

import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureSubImage2D;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD;

public class BindlessSprite {
    public BindlessTexture texture;
    Sprite original;
    public BindlessSprite(Sprite sprite) {
        this(sprite, 4);
    }

    public BindlessSprite(Sprite sprite, int miplevel) {
        this.original = sprite;
        //NOTE: Hardcoded mip map level, TODO: make it per texture
        texture = new BindlessTexture(sprite.getWidth(), sprite.getHeight(), miplevel+1);
        glTextureParameteri(texture.id, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTextureParameteri(texture.id, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glTextureParameteri(texture.id, GL_TEXTURE_MAX_LOD, miplevel);
        glTextureParameteri(texture.id, GL_TEXTURE_MAX_LEVEL, miplevel);

        //GL_TEXTURE_MAG_FILTER
        //GL_TEXTURE_MIN_FILTER

        glBindTexture(GL_TEXTURE_2D, texture.id);
        for (int i = 0; i < sprite.images.length; i++) {
            if (sprite.images[i].pointer == 0) continue;
            GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, sprite.images[i].getWidth());
            sprite.images[i].upload(i,0,0,0,0,sprite.getWidth()>>i, sprite.getHeight()>>i, true , false);
        }
    }

    public long getAddress() {
        //TODO: potenially invalidate the texture address if needed and shit, also only GENERATE the texture address
        // if this is called
        return texture.addr;
    }
}

