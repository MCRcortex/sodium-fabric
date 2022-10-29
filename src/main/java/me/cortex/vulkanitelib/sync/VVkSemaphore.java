package me.cortex.vulkanitelib.sync;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public class VVkSemaphore extends VVkObject {
    public final long semaphore;

    public VVkSemaphore(VVkDevice device, long semaphore) {
        super(device);
        this.semaphore = semaphore;
    }

    @Override
    public void free() {
        throw new IllegalStateException();
    }
}
