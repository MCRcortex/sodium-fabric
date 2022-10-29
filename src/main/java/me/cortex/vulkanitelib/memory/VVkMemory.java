package me.cortex.vulkanitelib.memory;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationInfo;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.util.vma.Vma.vmaMapMemory;
import static org.lwjgl.util.vma.Vma.vmaUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;

public class VVkMemory extends VVkObject {
    public final VVkAllocator allocator;
    public final long allocation;
    public final long memory;
    public final int type;
    public final long offset;
    public final long size;
    public VVkMemory(VVkAllocator allocator, long allocation, VmaAllocationInfo info) {
        super(allocator.device);
        this.allocator = allocator;
        this.allocation = allocation;
        this.memory = info.deviceMemory();
        this.type = info.memoryType();
        this.offset = info.offset();
        this.size = info.size();
    }

    public long mapVMA() {//Use vma to map, TODO: check this maps to the correct offset aswell
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pMappedBuff = stack.callocPointer(1);
            _CHECK_(vmaMapMemory(allocator.allocator, allocation, pMappedBuff));
            return pMappedBuff.get(0);
        }
    }

    public void unmapVMA() {
        vmaUnmapMemory(allocator.allocator, allocation);
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
