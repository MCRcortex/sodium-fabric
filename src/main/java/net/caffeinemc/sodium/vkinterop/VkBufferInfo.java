package net.caffeinemc.sodium.vkinterop;

public class VkBufferInfo {
    protected long id;
    protected long deviceMemory;

    protected long bufferSize;

    public VkBufferInfo(long id, long deviceMemory, long size) {
        this.id = id;
        this.deviceMemory = deviceMemory;
        this.bufferSize = size;
    }
}
