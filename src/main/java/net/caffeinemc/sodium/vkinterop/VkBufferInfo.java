package net.caffeinemc.sodium.vkinterop;

public class VkBufferInfo {
    public long offset;
    public long id;
    public long deviceMemory;
    public long allocation;

    public long bufferSize;

    public VkBufferInfo(long id, long allocation, long deviceMemory, long size, long offset) {
        this.id = id;
        this.allocation = allocation;
        this.deviceMemory = deviceMemory;
        this.bufferSize = size;
        this.offset = offset;
    }
}
