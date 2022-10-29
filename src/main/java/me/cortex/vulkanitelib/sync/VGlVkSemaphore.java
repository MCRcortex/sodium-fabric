package me.cortex.vulkanitelib.sync;

import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.VVkObject;

import static org.lwjgl.opengl.EXTSemaphore.*;

public class VGlVkSemaphore extends VVkSemaphore {
    private final int glSemaphore;
    private final long handle;
    public VGlVkSemaphore(VVkDevice device, int glSemaphore, long handle, long vkSemaphore) {
        super(device, vkSemaphore);
        this.glSemaphore = glSemaphore;
        this.handle = handle;
    }

    public VGlVkSemaphore glWait() {
        return glWait(new int[]{},new int[]{},new int[]{GL_LAYOUT_GENERAL_EXT});
    }

    public VGlVkSemaphore glWait(int[] buffers, int[] textures, int[] dstLayouts) {
        glWaitSemaphoreEXT(glSemaphore, buffers, textures, dstLayouts);
        return this;
    }

    public VGlVkSemaphore glSignal() {
        return glSignal(new int[]{},new int[]{},new int[]{GL_LAYOUT_GENERAL_EXT});
    }

    public VGlVkSemaphore glSignal(int[] buffers, int[] textures, int[] dstLayouts) {
        glSignalSemaphoreEXT(glSemaphore, buffers, textures, dstLayouts);
        return this;
    }
}
