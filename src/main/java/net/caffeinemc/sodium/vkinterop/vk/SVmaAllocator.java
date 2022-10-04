package net.caffeinemc.sodium.vkinterop.vk;

import net.caffeinemc.sodium.vkinterop.vk.memory.SVkBuffer;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVmaMemInfo;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.function.BiConsumer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class SVmaAllocator {
    public final SVkDevice device;
    protected final long allocator;

    public SVmaAllocator(SVkDevice device) {
        this(device, null);
    }

    public SVmaAllocator(SVkDevice device, BiConsumer<MemoryStack, VmaAllocatorCreateInfo> mutator) {
        this.device = device;

        try(MemoryStack stack = stackPush()) {
            VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(device.device.getPhysicalDevice().getInstance())
                    .physicalDevice(device.device.getPhysicalDevice())
                    .device(device.device)
                    .pVulkanFunctions(VmaVulkanFunctions
                            .calloc(stack)
                            .set(device.device.getPhysicalDevice().getInstance(), device.device))
                    .flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT);

            if (mutator != null) {
                mutator.accept(stack, allocatorCreateInfo);
            }

            PointerBuffer pAllocator = stack.pointers(VK_NULL_HANDLE);

            if (vmaCreateAllocator(allocatorCreateInfo, pAllocator) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            allocator = pAllocator.get(0);
        }
    }

    public SVkBuffer createBuffer(long size, int bufferUsage, int properties, int alignment) {
        return createBuffer(size, bufferUsage, properties, alignment, 0);
    }

    public SVkBuffer createBuffer(long size, int bufferUsage, int properties, int alignment, long pUserData) {
        return createBuffer(size, bufferUsage, properties, alignment, 0, pUserData);
    }

    protected SVkBuffer createBuffer(long size, int bufferUsage, int properties, int alignment, long pNext, long pUserData) {
        try (MemoryStack stack = stackPush()) {
            // create the final destination buffer
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo ai = VmaAllocationInfo.calloc(stack);
            _CHECK_(vmaCreateBuffer(allocator,
                            VkBufferCreateInfo
                                    .calloc(stack)
                                    .sType$Default()
                                    .size(size)
                                    .usage(bufferUsage | (pUserData != 0 ? VK_BUFFER_USAGE_TRANSFER_DST_BIT : 0))
                                    .pNext(pNext),
                            VmaAllocationCreateInfo
                                    .calloc(stack)
                                    .usage(VMA_MEMORY_USAGE_AUTO)
                                    .requiredFlags(properties)
                                    .pUserData(pUserData),
                            pBuffer,
                            pAllocation,
                            ai),
                    "Failed to allocate buffer");

            if ((ai.offset() % alignment) != 0)
                throw new AssertionError("Illegal offset alignment");
            SVmaMemInfo memInfo = new SVmaMemInfo(this, pAllocation.get(0), ai.deviceMemory(), ai.offset(), ai.size());
            return new SVkBuffer(memInfo, pBuffer.get(0), size);
        }
    }


    //TODO: Make more configurable type
    public SVkImage createImage(int width, int height, int mipLevel, int format, int usage, int properties) {
        return createImage(width, height, mipLevel, format, usage, properties, 0);
    }

    protected SVkImage createImage(int width, int height, int mipLevel, int format, int usage, int properties, long pNext) {
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo  imageInfo = VkImageCreateInfo .calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .pNext(pNext)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(mipLevel)
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
                    .requiredFlags(properties);

            LongBuffer pImage = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);

            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            _CHECK_(vmaCreateImage(allocator,
                    imageInfo,
                    allocationInfo,
                    pImage,
                    pAllocation,
                    info), "Failed to create image");
            return new SVkImage(
                    new SVmaMemInfo(this,
                            pAllocation.get(0),
                            info.deviceMemory(),
                            info.offset(),
                            info.size()),
                    pImage.get(0));
        }
    }
}
