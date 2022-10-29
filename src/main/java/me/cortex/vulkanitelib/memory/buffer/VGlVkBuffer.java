package me.cortex.vulkanitelib.memory.buffer;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;
import me.cortex.vulkanitelib.memory.VVkMemory;

public class VGlVkBuffer extends VVkBuffer {
    public final int glId;
    private int memObj;
    private long handle;
    public VGlVkBuffer(VVkMemory memory, int glId, int memObj, long handle, long buffer, long size) {
        super(memory, buffer, size);
        this.glId = glId;
        this.memObj = memObj;
        this.handle = handle;
    }
}
