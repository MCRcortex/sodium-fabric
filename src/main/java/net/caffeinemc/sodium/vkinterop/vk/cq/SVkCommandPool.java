package net.caffeinemc.sodium.vkinterop.vk.cq;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class SVkCommandPool {
    SVkDevice device;
    public long pool;
    public SVkCommandPool(SVkDevice device, int queueNodeIndex, int flags) {//TODO: make queueNodeIndex a S class type
        this.device = device;
        try(MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueNodeIndex)
                    .flags(flags);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(device.device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            pool = pCommandPool.get(0);
        }
    }



}
