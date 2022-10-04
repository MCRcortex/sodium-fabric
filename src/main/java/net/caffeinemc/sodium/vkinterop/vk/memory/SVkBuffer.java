package net.caffeinemc.sodium.vkinterop.vk.memory;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;
import org.lwjgl.vulkan.VkDeviceOrHostAddressKHR;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;

public class SVkBuffer {
    public final SVmaMemInfo memHandle;
    public final long buffer;
    public final long size;

    public SVkBuffer(SVmaMemInfo memHandle, long buffer, long size) {
        this.memHandle = memHandle;
        this.buffer = buffer;
        this.size = size;
    }

    public VkDeviceOrHostAddressKHR deviceAddress(MemoryStack stack, long offset, int alignment) {
        return VkDeviceOrHostAddressKHR
                .malloc(stack)
                .deviceAddress(bufferAddress(alignment) + offset);
    }
    public VkDeviceOrHostAddressConstKHR deviceAddressConst(MemoryStack stack, int alignment) {
        return deviceAddressConst(stack, 0, alignment);
    }
    public VkDeviceOrHostAddressConstKHR deviceAddressConst(MemoryStack stack, long offset, int alignment) {
        return VkDeviceOrHostAddressConstKHR
                .malloc(stack)
                .deviceAddress(bufferAddress(alignment) + offset);
    }

    public long bufferAddress(int alignment) {
        long address;
        try (MemoryStack stack = stackPush()) {
            address = vkGetBufferDeviceAddressKHR(memHandle.allocator.device.device, VkBufferDeviceAddressInfo
                    .calloc(stack)
                    .sType$Default()
                    .buffer(buffer));
        }
        // check alignment
        if ((address % alignment) != 0)
            throw new AssertionError("Illegal address alignment");
        return address;
    }

}
