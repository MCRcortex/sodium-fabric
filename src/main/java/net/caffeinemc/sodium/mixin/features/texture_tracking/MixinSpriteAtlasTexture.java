package net.caffeinemc.sodium.mixin.features.texture_tracking;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.sodium.render.chunk.draw.VulkanRenderer;
import net.caffeinemc.sodium.render.texture.SpriteUtil;
import net.caffeinemc.sodium.vkinterop.VkMemUtil;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkGlImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(SpriteAtlasTexture.class)
public class MixinSpriteAtlasTexture {

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V"))
    private void redirect(int id, int maxLevel, int width, int height) {
        SVkGlImage im = SVkDevice.INSTANCE.m_alloc_e.createVkGlImage(id, width, height, maxLevel+1, VK_FORMAT_R8G8B8A8_UNORM, GL_RGBA8, VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        GlStateManager._bindTexture(id);
        VulkanRenderer.TEXTURE_MAP.put(id, im);
    }

    @Inject(method = "getSprite", at = @At("RETURN"))
    private void preReturnSprite(CallbackInfoReturnable<Sprite> cir) {
        Sprite sprite = cir.getReturnValue();

        if (sprite != null) {
            SpriteUtil.markSpriteActive(sprite);
        }
    }
}
