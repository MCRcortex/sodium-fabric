package net.caffeinemc.sodium.vkinterop.vk;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.vk.cq.SVkCommandBuffer;
import net.caffeinemc.sodium.vkinterop.vk.cq.SVkCommandPool;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class SVkDevice {
    public static final SVkDevice INSTANCE = new SVkDevice(VkContextTEMP.getDevice());

    public final VkDevice device;
    public final SVmaAllocator m_alloc;//not exported memory
    public final SVmaAllocatorExported m_alloc_e;//exported memory
    public final SVkCommandPool transientCmdPool;
    private final VkContextTEMP.QueueFamilyIndices queueFamilies = VkContextTEMP.findQueueFamilies(VkContextTEMP.getDevice().getPhysicalDevice());
    public final int queueFamily = queueFamilies.graphicsFamily;//FIXME: THIS JUST ASSUMES ITS ALL THE SAME QUEUE FAMILY// = VkContextTEMP.findQueueFamilies(VkContextTEMP.getDevice().getPhysicalDevice());
    public final VkQueue queue;
    public SVkDevice(VkDevice device) {
        this.device = device;
        m_alloc = new SVmaAllocator(this);
        m_alloc_e = new SVmaAllocatorExported(this);
        transientCmdPool = new SVkCommandPool(this, queueFamily, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamily, 0, pQueue);
            queue = new VkQueue(pQueue.get(0), device);
        }
    }


    public VkCommandBuffer createAndBeginSingleTimeBuffer() {
        VkCommandBuffer buffer = SVkCommandBuffer.createOneTimeBuffer(this);
        try (MemoryStack stack = MemoryStack.stackPush()){
            _CHECK_(vkBeginCommandBuffer(buffer, VkCommandBufferBeginInfo
                            .calloc(stack)
                            .sType$Default()
                            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)),
                    "Failed to begin command buffer");
        }

        return buffer;
    }


    public long submitCommandBuffer(VkCommandBuffer commandBuffer, boolean endCommandBuffer, Runnable afterComplete) {
        if (endCommandBuffer)
            _CHECK_(vkEndCommandBuffer(commandBuffer),
                    "Failed to end command buffer");
        try (MemoryStack stack = stackPush()) {
            LongBuffer pFence = stack.mallocLong(1);
            _CHECK_(vkCreateFence(device, VkFenceCreateInfo
                            .calloc(stack)
                            .sType$Default(), null, pFence),
                    "Failed to create fence");
            _CHECK_(vkQueueSubmit(queue, VkSubmitInfo
                            .calloc(stack)
                            .sType$Default()
                            .pCommandBuffers(stack.pointers(commandBuffer)), pFence.get(0)),
                    "Failed to submit command buffer");
            long fence = pFence.get(0);
            //FIXME: TODO: THIS
            //if (afterComplete != null)
            //    waitingFenceActions.put(fence, afterComplete);
            return fence;
        }
    }

}
