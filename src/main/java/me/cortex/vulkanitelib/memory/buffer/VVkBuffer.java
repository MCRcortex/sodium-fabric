package me.cortex.vulkanitelib.memory.buffer;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.VVkMemory;
import me.cortex.vulkanitelib.memory.VVkMemoryObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;
import org.lwjgl.vulkan.VkDeviceOrHostAddressKHR;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;
import static org.lwjgl.vulkan.VK10.vkMapMemory;

public class VVkBuffer extends VVkMemoryObject {
    public final long buffer;
    public final long size;
    public VVkBuffer(VVkMemory memory, long buffer, long size) {
        super(memory);
        this.buffer = buffer;
        this.size = size;
    }

    public ByteBuffer map() {
        return MemoryUtil.memByteBuffer(memory.mapVMA(), (int)size);
    }
    public void unmap() {
        memory.unmapVMA();
    }

    @Override
    public void free() {
        vmaDestroyBuffer(memory.allocator.allocator, buffer, memory.allocation);
        memory.decRef();
    }

    public VkDeviceOrHostAddressKHR deviceAddress(MemoryStack stack, long offset) {
        return VkDeviceOrHostAddressKHR
                .malloc(stack)
                .deviceAddress(bufferAddress() + offset);
    }

    public VkDeviceOrHostAddressConstKHR deviceAddressConst(MemoryStack stack, long offset) {
        return VkDeviceOrHostAddressConstKHR
                .malloc(stack)
                .deviceAddress(bufferAddress() + offset);
    }

    public long bufferAddress() {
        try (MemoryStack stack = stackPush()) {
            return vkGetBufferDeviceAddressKHR(device.device, VkBufferDeviceAddressInfo
                    .calloc(stack)
                    .sType$Default()
                    .buffer(buffer));
        }
    }

    public VkDeviceOrHostAddressConstKHR deviceAddressConst(MemoryStack stack) {
        return deviceAddressConst(stack, 0);
    }
}
