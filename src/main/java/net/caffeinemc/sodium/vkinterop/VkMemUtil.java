package net.caffeinemc.sodium.vkinterop;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT;
import static org.lwjgl.opengl.EXTMemoryObject.glNamedBufferStorageMemEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.vmaCreateBuffer;
import static org.lwjgl.util.vma.Vma.vmaGetAllocationInfo;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.vkGetBufferMemoryRequirements2;

public class VkMemUtil {
    private static final VkDevice device = VkContextTEMP.getDevice();
    private static final long allocator = VkContextTEMP.getAllocator();
    private static LongOpenHashSet buffers = new LongOpenHashSet();
    private static long deviceMemory = 0;
    private static long nativeMemory = 0;

    public static void createBuffer(long size, int usage, int properties, LongBuffer pBuffer, PointerBuffer pBufferMemory) {
        try(MemoryStack stack = stackPush()) {

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(size);
            bufferInfo.usage(usage);
            //bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
//
            VmaAllocationCreateInfo allocationInfo  = VmaAllocationCreateInfo.callocStack(stack);
            //allocationInfo.usage(VMA_MEMORY_USAGE_CPU_ONLY);
            allocationInfo.requiredFlags(properties);

            int result = vmaCreateBuffer(allocator, bufferInfo, allocationInfo, pBuffer, pBufferMemory, null);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer:" + result);
            }

//            LongBuffer pBufferMem = MemoryUtil.memLongBuffer(MemoryUtil.memAddressSafe(pBufferMemory), 1);
//
//            if(vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to create vertex buffer");
//            }
//
//            VkMemoryRequirements memRequirements = VkMemoryRequirements.mallocStack(stack);
//            vkGetBufferMemoryRequirements(device, pBuffer.get(0), memRequirements);
//
//            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
//            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
//            allocInfo.allocationSize(memRequirements.size());
//            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));
//
//            if(vkAllocateMemory(device, allocInfo, null, pBufferMem) != VK_SUCCESS) {
//                throw new RuntimeException("Failed to allocate vertex buffer memory");
//            }
//
//            vkBindBufferMemory(device, pBuffer.get(0), pBufferMem.get(0), 0);

            if((properties & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) > 0) {
                deviceMemory += size;
            } else {
                nativeMemory += size;
            }

            buffers.add(pBuffer.get(0));
        }
    }


    public static VkBufferInfo createBuffer2(long size, int usage, int properties) {
        try(MemoryStack stack = stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pAllocation = stack.mallocLong(1);
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
            _CHECK_(vkCreateBuffer(device, bufferInfo, null, pBuffer), "Cannot make buffer");
            VkMemoryRequirements2 memReqs = VkMemoryRequirements2.calloc(stack).sType$Default();
            VkMemoryDedicatedRequirements dedicatedRegs = VkMemoryDedicatedRequirements.calloc(stack).sType$Default();
            VkBufferMemoryRequirementsInfo2 bufferReqs = VkBufferMemoryRequirementsInfo2.calloc(stack).sType$Default();
            memReqs.pNext(dedicatedRegs);
            bufferReqs.buffer(pBuffer.get(0));
            vkGetBufferMemoryRequirements2(VkContextTEMP.getDevice(), bufferReqs, memReqs);
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.memoryRequirements().size())
                    .memoryTypeIndex(findMemoryType(memReqs.memoryRequirements().memoryTypeBits(), properties));

            _CHECK_(vkAllocateMemory(device, allocInfo, null, pAllocation), "no allocaiutghek");

            vkBindBufferMemory(device, pBuffer.get(0), pAllocation.get(0), 0);
            return new VkBufferInfo(pBuffer.get(0), pAllocation.get(0), size);
        }
    }
    private static int findMemoryType(int typeFilter, int properties) {

        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.mallocStack();
        vkGetPhysicalDeviceMemoryProperties(VkContextTEMP.getDevice().getPhysicalDevice(), memProperties);

        for(int i = 0;i < memProperties.memoryTypeCount();i++) {
            if((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }

        throw new RuntimeException("Failed to find suitable memory type");
    }

    public static void bindVulkanBuffer(int glId, VkBufferInfo_VMA vkBufferInfo) {
        VmaAllocationInfo vai = VmaAllocationInfo.calloc();
        vmaGetAllocationInfo(VkContextTEMP.getAllocator(), vkBufferInfo.allocation, vai);
        vkBindBufferMemory(device, vkBufferInfo.id, vai.deviceMemory(), 0);
        VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc()
                .sType$Default()
                .memory(vai.deviceMemory())
                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        PointerBuffer pb = PointerBuffer.allocateDirect(1);
        _CHECK_(vkGetMemoryWin32HandleKHR(VkContextTEMP.getDevice(), info, pb), "allocation");
        long handle = pb.get(0);
        int memoryObject = glCreateMemoryObjectsEXT();
        VkMemoryRequirements req = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(VkContextTEMP.getDevice(), vkBufferInfo.id, req);
        glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
        glNamedBufferStorageMemEXT(glId, vkBufferInfo.bufferSize, memoryObject, 0);
    }

    public static void bindVulkanBuffer(int glId, VkBufferInfo vkBufferInfo) {
        VkMemoryGetWin32HandleInfoKHR info = VkMemoryGetWin32HandleInfoKHR.calloc()
                .sType$Default()
                .memory(vkBufferInfo.deviceMemory)
                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        PointerBuffer pb = PointerBuffer.allocateDirect(1);
        _CHECK_(vkGetMemoryWin32HandleKHR(VkContextTEMP.getDevice(), info, pb), "allocation");
        long handle = pb.get(0);
        //HANDLE IS NULL FOR SOME REASON
        int memoryObject = glCreateMemoryObjectsEXT();
        VkMemoryRequirements req = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(VkContextTEMP.getDevice(), vkBufferInfo.id, req);
        glImportMemoryWin32HandleEXT(memoryObject, req.size(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
        glNamedBufferStorageMemEXT(glId, vkBufferInfo.bufferSize, memoryObject, 0);
    }
}
