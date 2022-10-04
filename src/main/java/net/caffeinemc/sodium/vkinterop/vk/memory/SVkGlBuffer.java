package net.caffeinemc.sodium.vkinterop.vk.memory;

public class SVkGlBuffer extends SVkBuffer {
    public final int glId;
    public final long handle;
    public final long memoryObject;

    public SVkGlBuffer(SVmaMemInfo memHandle, long buffer, long size, int glId, long handle, long memoryObject) {
        super(memHandle, buffer, size);
        this.glId = glId;
        this.handle = handle;
        this.memoryObject = memoryObject;
    }
}
