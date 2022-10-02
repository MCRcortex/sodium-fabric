package net.caffeinemc.sodium.vkinterop.vk.memory.images;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class SVkImageView {
    SVkDevice device;
    SVkImage image;
    public long view;
    //TODO: MAKE MORE CONFIGURABLE/derive more from SVkImage
    public SVkImageView(SVkDevice device, SVkImage image, int format, int aspectFlags, int mipLevels) {
        this.device = device;
        this.image = image;
        try (MemoryStack stack = stackPush()) {

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image.image)
                    .format(format)
                    .viewType(VK_IMAGE_TYPE_2D);
            viewInfo.subresourceRange()
                    .aspectMask(aspectFlags)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1);
            LongBuffer pImageView = stack.mallocLong(1);
            if(vkCreateImageView(device.device, viewInfo, null, pImageView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture image view");
            }

            this.view = pImageView.get(0);
        }
    }
}
