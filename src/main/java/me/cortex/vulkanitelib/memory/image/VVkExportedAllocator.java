package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.memory.VVkAllocator;
import me.cortex.vulkanitelib.memory.VVkMemory;
import me.cortex.vulkanitelib.memory.buffer.VGlVkBuffer;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class VVkExportedAllocator extends VVkAllocator {
    public final long allocator;
    public VVkExportedAllocator(VVkDevice device) {
        super(device, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(device.device.getPhysicalDevice().getInstance())
                    .physicalDevice(device.device.getPhysicalDevice())
                    .device(device.device)
                    .pVulkanFunctions(VmaVulkanFunctions
                            .calloc(stack)
                            .set(device.device.getPhysicalDevice().getInstance(), device.device))
                    //.flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
                    ;

            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(device.device.getPhysicalDevice(), memoryProperties);

            IntBuffer handleTypes = MemoryUtil.memAllocInt(memoryProperties.memoryTypeCount());
            for (int i = 0; i < handleTypes.capacity(); i++) {
                handleTypes.put(i, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            }
            allocatorCreateInfo.pTypeExternalMemoryHandleTypes(handleTypes);


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

    public VGlVkBuffer createSharedBuffer(long size, int bufferUsage, int properties) {
        return createSharedBuffer(glCreateBuffers(), size, bufferUsage, properties);
    }
    public VGlVkBuffer createSharedBuffer(int glId, long size, int bufferUsage, int properties) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo ai = VmaAllocationInfo.calloc(stack);
            VkExternalMemoryBufferCreateInfo extra = VkExternalMemoryBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            _CHECK_(vmaCreateBuffer(allocator,
                            VkBufferCreateInfo
                                    .calloc(stack)
                                    .sType$Default()
                                    .size(size)
                                    .usage(bufferUsage)
                                    .pNext(extra),
                            VmaAllocationCreateInfo
                                    .calloc(stack)
                                    .usage(VMA_MEMORY_USAGE_AUTO)
                                    .requiredFlags(properties)
                                    //.flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)//FIXME: just hack atm cause it doesnt work with non 0 offsets
                            ,
                            pBuffer,
                            pAllocation,
                            ai),
                    "Failed to allocate buffer");



            VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(ai.deviceMemory())
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            PointerBuffer pb = stack.callocPointer(1);
            _CHECK_(vkGetMemoryWin32HandleKHR(device.device, info, pb));
            long handle = pb.get(0);
            if (handle == 0)
                throw new IllegalStateException();

            int memoryObject = glCreateMemoryObjectsEXT();

            VkMemoryRequirements req = VkMemoryRequirements.calloc();
            vkGetBufferMemoryRequirements(device.device, pBuffer.get(0), req);
            glImportMemoryWin32HandleEXT(memoryObject, req.size()+ai.offset(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);

            glNamedBufferStorageMemEXT(glId, size, memoryObject, ai.offset());//vkBuffer.memHandle.offset

            return new VGlVkBuffer(new VVkMemory(this, pAllocation.get(0), ai), glId, memoryObject, handle, pBuffer.get(0), size);
        }
    }


    public VGlVkImage createShared2DImage(int width, int height, int mipLevels, int format, int glFormat, int usage, int properties) {
        return createShared2DImage(glCreateTextures(GL_TEXTURE_2D), width, height, mipLevels, format, glFormat, usage, properties);
    }
    public VGlVkImage createShared2DImage(int glId, int width, int height, int mipLevels, int format, int glFormat, int usage, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            LongBuffer pImage = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);

            VmaAllocationInfo ai = VmaAllocationInfo.calloc(stack);
            {

                VkExternalMemoryImageCreateInfo extra = VkExternalMemoryImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
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
                        //.sharingMode(VK_SHARING_MODE_CONCURRENT)
                        .pNext(extra);
                imageInfo.extent().width(width);
                imageInfo.extent().height(height);
                imageInfo.extent().depth(1);

                VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                        .requiredFlags(properties).usage(VMA_MEMORY_USAGE_AUTO)
                        //.flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)//FIXME: THIS IS JUST A HACK TO GET SHIT WORKING RIGHT as the offset is 0
                        ;

                _CHECK_(vmaCreateImage(allocator,
                        imageInfo,
                        allocationInfo,
                        pImage,
                        pAllocation,
                        ai), "Failed to create image");
            }


            VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(ai.deviceMemory())
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            PointerBuffer pb = stack.callocPointer(1);
            _CHECK_(vkGetMemoryWin32HandleKHR(device.device, info, pb));
            long handle = pb.get(0);
            if (handle == 0)
                throw new IllegalStateException();


            VkMemoryRequirements req = VkMemoryRequirements.calloc();
            vkGetImageMemoryRequirements(device.device, pImage.get(0), req);

            int memoryObject = glCreateMemoryObjectsEXT();
            glImportMemoryWin32HandleEXT(memoryObject, req.size() + ai.offset(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);

            glTextureStorageMem2DEXT(glId, mipLevels, glFormat, width, height, memoryObject, ai.offset());
            glTextureParameteri(glId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTextureParameteri(glId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTextureParameteri(glId, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTextureParameteri(glId, GL_TEXTURE_WRAP_T, GL_REPEAT);

            return new VGlVkImage(glId, memoryObject, handle, width, height, 1, mipLevels, 1, VK_IMAGE_TYPE_2D, format, VK_IMAGE_LAYOUT_UNDEFINED, pImage.get(0),
                    new VVkMemory(this, pAllocation.get(0), ai));
        }
    }
}
