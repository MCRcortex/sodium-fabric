package net.caffeinemc.sodium.mixin.features.texture_tracking;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures;
import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;

@Mixin(AbstractTexture.class)
public class MixinAbstractTexture {
    @Shadow protected int glId;

    @Overwrite
    public int getGlId() {
        RenderSystem.assertOnRenderThreadOrInit();
        if (this.glId == -1) {
            this.glId = glCreateTextures(GL_TEXTURE_2D);
        }

        return this.glId;
    }

}
