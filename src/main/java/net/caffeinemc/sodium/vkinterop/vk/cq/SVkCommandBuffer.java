package net.caffeinemc.sodium.vkinterop.vk.cq;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;

public class SVkCommandBuffer {
    public static VkCommandBuffer[] createCommandBuffers(SVkDevice device, SVkCommandPool pool, int count) {
        VkCommandBuffer[] buffers = new VkCommandBuffer[count];
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(pool.pool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(count);
            PointerBuffer pCommandBuffers = memAllocPointer(count);
            _CHECK_(vkAllocateCommandBuffers(device.device, cmdBufAllocateInfo, pCommandBuffers), "failed to allocate command buffers");
            for (int i = 0; i < count; i++) {
                buffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device.device);
            }
        }
        return buffers;
    }

    public static VkCommandBuffer createOneTimeBuffer(SVkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(device.transientCmdPool.pool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pCommandBuffers = memAllocPointer(1);
            _CHECK_(vkAllocateCommandBuffers(device.device, cmdBufAllocateInfo, pCommandBuffers), "failed to allocate command buffers");
            return new VkCommandBuffer(pCommandBuffers.get(0), device.device);
        }
    }
}
