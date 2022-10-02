package net.caffeinemc.sodium.mixin.features.texture_tracking;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.caffeinemc.sodium.render.chunk.draw.VulkanRenderer;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkGlImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

@Mixin(TextureUtil.class)
public class MixinTextureUtil {
    @Overwrite
    public static void prepareImage(int id, int width, int height) {
        SVkGlImage im = SVkDevice.INSTANCE.m_alloc_e.createVkGlImage(id, width, height, 1, VK_FORMAT_R8G8B8A8_UNORM, GL_RGBA8, VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        GlStateManager._bindTexture(id);
        VulkanRenderer.TEXTURE_MAP.put(id, im);
    }
}
