package net.caffeinemc.sodium.vkinterop.vk;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import net.caffeinemc.sodium.vkinterop.VkUtils;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkBuffer;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkGlBuffer;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVmaMemInfo;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkGlImage;
import net.caffeinemc.sodium.vkinterop.vk.memory.images.SVkImage;
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
import java.util.function.BiConsumer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateTextures;
import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class SVmaAllocatorExported extends SVmaAllocator {

    public SVmaAllocatorExported(SVkDevice device) {
        super(device, (stack, aci) -> {
            IntBuffer handleTypes = MemoryUtil.memAllocInt(5);
            //FIXME: make it only on specific types or something, BETTER YET USE A VmaPool instead
            handleTypes.put(0, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);//VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            handleTypes.put(1, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);//VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            handleTypes.put(2, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);//VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            handleTypes.put(3, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);//VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            handleTypes.put(4, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);//VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT
            aci.pTypeExternalMemoryHandleTypes(handleTypes);
        });
    }

    public SVkBuffer createBuffer(long size, int bufferUsage, int properties, int alignment) {
        return createBuffer(size, bufferUsage, properties, alignment, 0);
    }

    public SVkBuffer createBuffer(long size, int bufferUsage, int properties, int alignment, long pUserData) {
        try (MemoryStack stack = stackPush()) {
            VkExternalMemoryBufferCreateInfo extra = VkExternalMemoryBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            return createBuffer(size, bufferUsage, properties, alignment, extra.address(), pUserData);
        }
    }

    public SVkGlBuffer createVkGlBuffer(long size, int bufferUsage, int properties, int alignment) {
        try (MemoryStack stack = stackPush()) {
            SVkBuffer vkBuffer = createBuffer(size, bufferUsage, properties, alignment);

            VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(vkBuffer.memHandle.memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            PointerBuffer pb = stack.callocPointer(1);
            VkUtils._CHECK_(vkGetMemoryWin32HandleKHR(device.device, info, pb), "allocation");
            long handle = pb.get(0);
            if (handle == 0)
                throw new IllegalStateException();
            int memoryObject = glCreateMemoryObjectsEXT();
            VkMemoryRequirements req = VkMemoryRequirements.calloc();
            vkGetBufferMemoryRequirements(VkContextTEMP.getDevice(), vkBuffer.buffer, req);
            glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
            int glId = glCreateBuffers();
            glNamedBufferStorageMemEXT(glId, size, memoryObject, 0);//vkBuffer.memHandle.offset
            return new SVkGlBuffer(vkBuffer.memHandle, vkBuffer.buffer, glId, handle, memoryObject);
        }
    }

    @Override
    public SVkImage createImage(int width, int height, int mipLevel, int format, int usage, int properties) {
        try (MemoryStack stack = stackPush()) {
            VkExternalMemoryImageCreateInfo extra = VkExternalMemoryImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            return createImage(width, height, mipLevel, format, usage, properties, extra.address());
        }
    }

    public SVkGlImage createVkGlImage(int width, int height, int format, int glFormat, int usage) {
        return createVkGlImage(glCreateTextures(GL_TEXTURE_2D), width, height, 1, format, glFormat, usage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }
    public SVkGlImage createVkGlImage(int glId, int width, int height, int format, int glFormat, int usage) {
        return createVkGlImage(glId, width, height, 1, format, glFormat, usage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }
    public SVkGlImage createVkGlImage(int glId, int width, int height, int mipLevel, int format, int glFormat, int usage, int properties) {
        try (MemoryStack stack = stackPush()) {
            SVkImage vkImage = createImage(width, height, mipLevel, format, usage, properties);

            VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(vkImage.memHandle.memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            PointerBuffer pb = stack.callocPointer(1);
            VkUtils._CHECK_(vkGetMemoryWin32HandleKHR(device.device, info, pb), "allocation");
            long handle = pb.get(0);
            if (handle == 0)
                throw new IllegalStateException();
            int memoryObject = glCreateMemoryObjectsEXT();
            VkMemoryRequirements req = VkMemoryRequirements.calloc();
            vkGetImageMemoryRequirements(VkContextTEMP.getDevice(), vkImage.image, req);
            glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
            glTextureStorageMem2DEXT(glId, mipLevel, glFormat, width, height, memoryObject, 0);//vkImage.memHandle.offset
            return new SVkGlImage(vkImage.memHandle, vkImage.image, glId, handle, memoryObject);
        }
    }


}
