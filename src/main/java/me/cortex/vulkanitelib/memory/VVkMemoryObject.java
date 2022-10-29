package me.cortex.vulkanitelib.memory;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

public abstract class VVkMemoryObject extends VVkObject {
    public final VVkMemory memory;
    protected VVkMemoryObject(VVkMemory memory) {
        super(memory.device);
        this.memory = memory;
    }
}
