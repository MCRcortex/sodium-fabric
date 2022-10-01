package net.caffeinemc.sodium.vkinterop.vk.memory;

public class SVkGlImage extends SVkImage {
    public final int glId;
    public final long handle;
    public final long memoryObject;

    public SVkGlImage(SVmaMemInfo memHandle, long image, int glId, long handle, long memoryObject) {
        super(memHandle, image);
        this.glId = glId;
        this.handle = handle;
        this.memoryObject = memoryObject;
    }
}
