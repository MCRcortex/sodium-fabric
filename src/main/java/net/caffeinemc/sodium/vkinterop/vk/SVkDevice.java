package net.caffeinemc.sodium.vkinterop.vk;

import net.caffeinemc.sodium.vkinterop.VkContextTEMP;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class SVkDevice {
    public static final SVkDevice INSTANCE = new SVkDevice(VkContextTEMP.getDevice());

    public final VkDevice device;
    public final SVmaAllocator m_alloc;//not exported memory
    public final SVmaAllocatorExported m_alloc_e;//exported memory
    public SVkDevice(VkDevice device) {
        this.device = device;
        m_alloc = new SVmaAllocator(this);
        m_alloc_e = new SVmaAllocatorExported(this);
    }


}
