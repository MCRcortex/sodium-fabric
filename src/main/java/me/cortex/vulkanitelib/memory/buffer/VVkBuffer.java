package me.cortex.vulkanitelib.memory.buffer;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.VVkMemory;
import me.cortex.vulkanitelib.memory.VVkMemoryObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
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
        throw new IllegalStateException();
    }
}
