package net.caffeinemc.sodium.vkinterop.vk.memory.images;

import net.caffeinemc.sodium.vkinterop.vk.memory.SVmaMemInfo;

public class SVkImage {
    public final SVmaMemInfo memHandle;
    public final long image;

    public SVkImage(SVmaMemInfo memHandle, long image) {
        this.memHandle = memHandle;
        this.image = image;
    }
}
