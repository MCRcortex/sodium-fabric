package me.cortex.vulkanitelib.sync;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public class VVkFence extends VVkObject {
    public final long fence;

    protected VVkFence(VVkDevice device, long fence) {
        super(device);
        this.fence = fence;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
