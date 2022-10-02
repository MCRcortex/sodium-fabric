package net.caffeinemc.sodium.vkinterop;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVmaMemInfo;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkGlImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.vkGetBufferMemoryRequirements2;

public class VkMemUtil {
    private static final VkDevice device = VkContextTEMP.getDevice();
    private static final long allocator = VkContextTEMP.getAllocator();


    public static class VkImageInfo {
        protected long id;
        protected long deviceMemory;
        protected long offset;

        public VkImageInfo(long id, long deviceMemory, long offset) {
            this.id = id;
            this.deviceMemory = deviceMemory;
            this.offset = offset;
        }
    }
    public static VkImageInfo createExportedTextureVMA(int width, int height, int mipLevel, int format, int tiling, int usage, int properties) {
        try(MemoryStack stack = stackPush()) {
            VkExternalMemoryImageCreateInfo extra = VkExternalMemoryImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            VkImageCreateInfo  imageInfo = VkImageCreateInfo .calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .pNext(extra)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(mipLevel)
                    .arrayLayers(1)
                    .format(format)
                    .tiling(tiling)
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

            LongBuffer pBuffer = stack.callocLong(1);
            PointerBuffer pBufferMemory = stack.callocPointer(1);

            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            int result = vmaCreateImage(VkContextTEMP.getAllocator(),
                    imageInfo,
                    allocationInfo,
                    pBuffer,
                    pBufferMemory,
                    info);

            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer:" + VkUtils.translateVulkanResult(result));
            }
            return new VkImageInfo(pBuffer.get(0), info.deviceMemory(), info.offset());
        }
    }

    public static VkBufferInfo createExportedBufferVMA(long size, int usage, int properties) {
        try(MemoryStack stack = stackPush()) {
            VkExternalMemoryBufferCreateInfo extra = VkExternalMemoryBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .pNext(extra)
                    .size(size);

            VmaAllocationCreateInfo allocationInfo  = VmaAllocationCreateInfo.calloc(stack)
                    .requiredFlags(properties);

            LongBuffer pBuffer = stack.callocLong(1);
            PointerBuffer pBufferMemory = stack.callocPointer(1);

            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            int result = vmaCreateBuffer(VkContextTEMP.getAllocator(),
                    bufferInfo,
                    allocationInfo,
                    pBuffer,
                    pBufferMemory,
                    info);

            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer:" + VkUtils.translateVulkanResult(result));
            }
            return new VkBufferInfo(pBuffer.get(0), pBufferMemory.get(0), info.deviceMemory(), size, info.offset());
        }
    }

    public static void generateVKBuffer(int glObj, VkBufferInfo vkBuffer)  {
        VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc()
                .sType$Default()
                .memory(vkBuffer.deviceMemory)
                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        PointerBuffer pb = PointerBuffer.allocateDirect(1);
        VkUtils._CHECK_(vkGetMemoryWin32HandleKHR(VkContextTEMP.getDevice(), info, pb), "allocation");
        long handle = pb.get(0);
        int memoryObject = glCreateMemoryObjectsEXT();
        VkMemoryRequirements req = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(VkContextTEMP.getDevice(), vkBuffer.id, req);
        glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
        glNamedBufferStorageMemEXT(glObj, vkBuffer.bufferSize, memoryObject, vkBuffer.offset);
    }

    public static void generateVKTexture(int glObj, int miplevels, int width, int height)  {
        VkImageInfo imgInfo = createExportedTextureVMA(width, height, miplevels,
                VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc()
                .sType$Default()
                .memory(imgInfo.deviceMemory)
                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        PointerBuffer pb = PointerBuffer.allocateDirect(1);
        VkUtils._CHECK_(vkGetMemoryWin32HandleKHR(VkContextTEMP.getDevice(), info, pb), "allocation");
        long handle = pb.get(0);
        int memoryObject = glCreateMemoryObjectsEXT();
        VkMemoryRequirements req = VkMemoryRequirements.calloc();
        vkGetImageMemoryRequirements(VkContextTEMP.getDevice(), imgInfo.id, req);
        glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);

        glTextureStorageMem2DEXT(glObj, miplevels, GL_RGBA8, width,height, memoryObject, imgInfo.offset);
    }

}
