package me.cortex.vulkanitelib.raytracing;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkDeviceOrHostAddressConstKHR;

import static org.lwjgl.vulkan.KHRAccelerationStructure.vkDestroyAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR;

public class VVkAccelerationStructure extends VVkObject {
    public final long acceleration;
    public VVkBuffer buffer;
    public VVkAccelerationStructure(VVkDevice device, long acceleration, VVkBuffer accelerationBuffer) {
        super(device);
        this.acceleration = acceleration;
        this.buffer = accelerationBuffer;
    }

    @Override
    public void free() {
        vkDestroyAccelerationStructureKHR(device.device, acceleration, null);
        buffer.free();
    }

    public long deviceAddress() {
        try (MemoryStack stack = MemoryStack.stackPush()){
            return vkGetAccelerationStructureDeviceAddressKHR(device.device,
                    VkAccelerationStructureDeviceAddressInfoKHR
                            .calloc(stack)
                            .sType$Default()
                            .accelerationStructure(acceleration));
        }
    }
}
