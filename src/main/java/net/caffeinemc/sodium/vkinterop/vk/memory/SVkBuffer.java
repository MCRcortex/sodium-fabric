package net.caffeinemc.sodium.vkinterop.vk.memory;

public class SVkBuffer {
    public final SVmaMemInfo memHandle;
    public final long buffer;

    public SVkBuffer(SVmaMemInfo memHandle, long buffer) {
        this.memHandle = memHandle;
        this.buffer = buffer;
    }
}
