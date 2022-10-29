package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public class VVkSampler extends VVkObject {
    public final long sampler;

    public VVkSampler(VVkDevice device, long sampler) {
        super(device);
        this.sampler = sampler;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
