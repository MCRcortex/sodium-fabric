package net.caffeinemc.sodium.vkinterop;

public class VkBufferInfo_VMA {
    protected long id;
    protected long allocation;

    protected int bufferSize;

    public VkBufferInfo_VMA(long id, long allocation, int size) {
        this.id = id;
        this.allocation = allocation;
        this.bufferSize = size;
    }
}
