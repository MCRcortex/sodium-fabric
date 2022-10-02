package net.caffeinemc.sodium.vkinterop.vk.memory.images;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.pipeline.SVkRenderPass;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;

public class SVkFramebuffer {
    SVkDevice device;
    public long framebuffer;
    public SVkFramebuffer(SVkDevice device, SVkRenderPass renderPass, int width, int height, SVkImageView... attachments) {
        this.device = device;
        try (MemoryStack stack = stackPush()) {
            LongBuffer attach = stack.mallocLong(attachments.length);
            for (int i = 0; i < attachments.length; i++) {
                attach.put(attachments[i].view);
            }
            attach.rewind();
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass.renderPass)
                    .width(width)
                    .height(height)
                    .layers(1)//TODO: Make configurable
                    .pAttachments(attach);

            LongBuffer pFramebuffer = stack.mallocLong(1);
            _CHECK_(vkCreateFramebuffer(device.device, framebufferInfo, null, pFramebuffer), "Failed to create framebuffer");
            framebuffer = pFramebuffer.get(0);
        }
    }
}
