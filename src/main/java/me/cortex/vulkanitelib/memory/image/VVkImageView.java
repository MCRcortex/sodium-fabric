package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public class VVkImageView extends VVkObject {
    public final long view;
    public final VVkImage image;
    public final int aspects;
    public final int layers;

    protected VVkImageView(VVkDevice device, int aspects, int layers, VVkImage image, long imageview) {
        super(device);
        this.image = image;
        this.view = imageview;
        this.layers = layers;
        this.aspects = aspects;
    }


    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
