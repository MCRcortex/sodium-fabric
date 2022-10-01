package net.caffeinemc.sodium.vkinterop.vk.memory;

import net.caffeinemc.sodium.vkinterop.vk.SVmaAllocator;

public class SVmaMemInfo {
    public final SVmaAllocator allocator;
    public final long memory;//VkDeviceMemory
    public final long offset;//VkDeviceSize
    public final long size;//VkDeviceSize
    public final long allocation;//VmaAllocation

    public SVmaMemInfo(SVmaAllocator allocator, long allocation, long memory, long offset, long size) {
        this.allocator = allocator;
        this.memory = memory;
        this.offset = offset;
        this.size = size;
        this.allocation = allocation;
    }
}
