package net.caffeinemc.sodium.vkinterop.vk.memory;

public class SVkImage {
    public final SVmaMemInfo memHandle;
    public final long image;

    public SVkImage(SVmaMemInfo memHandle, long image) {
        this.memHandle = memHandle;
        this.image = image;
    }
}
