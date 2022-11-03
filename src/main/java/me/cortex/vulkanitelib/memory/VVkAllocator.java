package me.cortex.vulkanitelib.memory;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.memory.buffer.VGlVkBuffer;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import me.cortex.vulkanitelib.memory.image.VVkImage;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryGetWin32HandleInfoKHR;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class VVkAllocator extends VVkObject {
    public final long allocator;
    public VVkAllocator(VVkDevice device, int flags) {
        super(device);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(device.device.getPhysicalDevice().getInstance())
                    .physicalDevice(device.device.getPhysicalDevice())
                    .device(device.device)
                    .pVulkanFunctions(VmaVulkanFunctions
                            .calloc(stack)
                            .set(device.device.getPhysicalDevice().getInstance(), device.device))
                    .flags(flags)
                    ;

            PointerBuffer pAllocator = stack.pointers(VK_NULL_HANDLE);

            if (vmaCreateAllocator(allocatorCreateInfo, pAllocator) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            allocator = pAllocator.get(0);
        }
    }
    @Override
    public void free() {
        throw new IllegalStateException();
    }

    public VVkBuffer createBuffer(long size, int bufferUsage, int properties) {
        return createBuffer(size, bufferUsage, properties, 0);
    }
    public VVkBuffer createBuffer(long size, int bufferUsage, int properties, int flags) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo ai = VmaAllocationInfo.calloc(stack);
            _CHECK_(vmaCreateBuffer(allocator,
                            VkBufferCreateInfo
                                    .calloc(stack)
                                    .sType$Default()
                                    .size(size)
                                    .usage(bufferUsage),
                            VmaAllocationCreateInfo
                                    .calloc(stack)
                                    .usage(VMA_MEMORY_USAGE_AUTO)
                                    .requiredFlags(properties)
                                    .flags(flags),
                            pBuffer,
                            pAllocation,
                            ai),
                    "Failed to allocate buffer");
            return new VVkBuffer(new VVkMemory(this, pAllocation.get(0), ai), pBuffer.get(0), size);
        }
    }

    public VVkBuffer createBuffer(ByteBuffer data, int bufferUsage, int properties) {
        try (MemoryStack stack = stackPush()) {
            VVkBuffer buffer = createBuffer(data.limit(), bufferUsage, properties);
            //Create temporary upload buffer
            VVkBuffer stageBuffer = createBuffer(data.limit(),VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            stageBuffer.map().put(data);//TODO: maybe make a memCpy
            stageBuffer.unmap();
            VVkCommandBuffer cmd = device.transientPool.createCommandBuffer();
            cmd.begin();
            vkCmdCopyBuffer(cmd.buffer, stageBuffer.buffer, buffer.buffer, VkBufferCopy
                    .calloc(1, stack)
                    .size(data.limit()));
            cmd.end();
            device.fetchQueue().submit(cmd, stageBuffer::free);
            return buffer;
        }
    }

    public VVkImage create2DImage(int width, int height, int mipLevels, int format, int usage, int properties) {
        return create2DImage(width, height, mipLevels, format, usage, properties, 0);
    }
    public VVkImage create2DImage(int width, int height, int mipLevels, int format, int usage, int properties, long userData) {
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo .calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .format(format)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .sharingMode(VK_SHARING_MODE_CONCURRENT)
                    .pQueueFamilyIndices(stack.ints(0,1));
            imageInfo.extent().width(width);
            imageInfo.extent().height(height);
            imageInfo.extent().depth(1);

            VmaAllocationCreateInfo allocationInfo  = VmaAllocationCreateInfo.calloc(stack)
                    .requiredFlags(properties)
                    .usage(VMA_MEMORY_USAGE_AUTO).pUserData(userData);

            LongBuffer pImage = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);

            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            _CHECK_(vmaCreateImage(allocator,
                    imageInfo,
                    allocationInfo,
                    pImage,
                    pAllocation,
                    info), "Failed to create image");
            return new VVkImage(width, height, 1, mipLevels, 1, VK_IMAGE_TYPE_2D, format, VK_IMAGE_LAYOUT_UNDEFINED, pImage.get(0),
                    new VVkMemory(this, pAllocation.get(0), info));
        }
    }

}
