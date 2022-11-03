package me.cortex.vulkanitelib.other;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;

public class VVkCommandPool extends VVkObject {
    public final long pool;
    public VVkCommandPool(VVkDevice device, long pool) {
        super(device);
        this.pool = pool;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkCommandBuffer createCommandBuffer() {
        return createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
    }

    public VVkCommandBuffer[] createCommandBuffers(int count) {
        return createCommandBuffers(count, VK_COMMAND_BUFFER_LEVEL_PRIMARY);
    }

    public VVkCommandBuffer createCommandBuffer(int level) {
        return createCommandBuffers(1, level)[0];
    }

    public synchronized VVkCommandBuffer[] createCommandBuffers(int count, int level) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer pCommandBuffer = stack.mallocPointer(count);
            _CHECK_(vkAllocateCommandBuffers(device.device,
                            VkCommandBufferAllocateInfo
                                    .calloc(stack)
                                    .sType$Default()
                                    .commandPool(pool)
                                    .level(level)
                                    .commandBufferCount(count), pCommandBuffer),
                    "Failed to create command buffer");
            VVkCommandBuffer[] buffers = new VVkCommandBuffer[count];
            for (int i = 0; i < count; i++) {
                buffers[i] = new VVkCommandBuffer(device, this, new VkCommandBuffer(pCommandBuffer.get(i), device.device));
            }
            return buffers;
        }
    }
}
