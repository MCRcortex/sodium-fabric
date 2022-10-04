package net.caffeinemc.sodium.vkinterop.vk.raytracing;

import net.caffeinemc.sodium.vkinterop.vk.SVkDevice;
import net.caffeinemc.sodium.vkinterop.vk.memory.SVkBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static net.caffeinemc.sodium.vkinterop.VkUtils._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;

public class SVkAccelerationStructure {
    SVkDevice device;
    public long structure;
    SVkBuffer buffer;
    public SVkAccelerationStructure(SVkDevice device, long structure, SVkBuffer buffer) {
        this.device = device;
        this.structure = structure;
        this.buffer = buffer;
    }
}
