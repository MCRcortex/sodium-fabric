package me.cortex.vulkanitelib.memory.image;

import me.cortex.vulkanitelib.memory.VVkMemory;

public class VGlVkImage extends VVkImage {
    public final int glId;
    final int glMemoryObj;
    final long handle;
    public VGlVkImage(int glId, int glMemoryObj, long handle, int width, int height, int depth, int mipLayers, int arrayLayers, int type, int format, int initialLayout, long image, VVkMemory memory) {
        super(width, height, depth, mipLayers, arrayLayers, type, format, initialLayout, image, memory);
        this.glId = glId;
        this.glMemoryObj = glMemoryObj;
        this.handle = handle;
    }
}
