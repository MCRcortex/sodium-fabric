package net.caffeinemc.sodium.mixin.features.texture_tracking;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.sodium.render.texture.SpriteUtil;
import net.caffeinemc.sodium.vkinterop.VkMemUtil;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteAtlasTexture.class)
public class MixinSpriteAtlasTexture {

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        VkMemUtil.generateVKTexture(id, maxLevel+1, width, height);
        GlStateManager._bindTexture(id);
    }

    @Inject(method = "getSprite", at = @At("RETURN"))
    private void preReturnSprite(CallbackInfoReturnable<Sprite> cir) {
        Sprite sprite = cir.getReturnValue();

        if (sprite != null) {
            SpriteUtil.markSpriteActive(sprite);
        }
    }
}
