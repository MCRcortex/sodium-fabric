package me.cortex.nv.util;

import me.cortex.nv.gl.RenderDevice;
import me.cortex.nv.util.SegmentedManager;

public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    private final int vertexFormatSize;
    private final RenderDevice device;

    public BufferArena(RenderDevice device, int vertexFormatSize) {
        this.device = device;
        this.vertexFormatSize = vertexFormatSize;
    }

    public void delete() {

    }
}
