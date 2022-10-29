package net.caffeinemc.sodium.mixin.core;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import net.caffeinemc.sodium.vk.VulkanContext;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

@Mixin(Framebuffer.class)
public class MixinFramebuffer {
    @Shadow
    protected int colorAttachment;

    @Shadow
    protected int depthAttachment;

    @Inject(method = "initFbo", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;checkFramebufferStatus()V", ordinal = 0))
    private void changeTexture(int width, int height, boolean getError, CallbackInfo ci) {
        GlStateManager._deleteTexture(colorAttachment);

        VGlVkImage im = VulkanContext.device.exportedAllocator.createShared2DImage(width,height, 1, VK_FORMAT_R8G8B8A8_UNORM, GL11.GL_RGBA8, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        this.colorAttachment = im.glId;
        GlStateManager._bindTexture(this.colorAttachment);
        VulkanContext.gl2vk_textures.put(this.colorAttachment, im);
        VulkanContext.colorTex = im;

        GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, this.colorAttachment, 0);

        GlStateManager._deleteTexture(depthAttachment);

        VGlVkImage db = VulkanContext.device.exportedAllocator.createShared2DImage(width,height, 1, VK_FORMAT_D24_UNORM_S8_UINT, GL_DEPTH24_STENCIL8, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT , VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        this.depthAttachment = db.glId;
        GlStateManager._bindTexture(this.depthAttachment);
        VulkanContext.gl2vk_textures.put(this.depthAttachment, db);
        VulkanContext.depthTex = db;

        GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, this.depthAttachment, 0);

    }
}