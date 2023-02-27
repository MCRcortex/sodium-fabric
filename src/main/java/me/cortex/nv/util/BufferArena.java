package me.cortex.nv.util;

import me.cortex.nv.util.SegmentedManager;

public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    private final int vertexFormatSize;

    public BufferArena(int vertexFormatSize) {
        this.vertexFormatSize = vertexFormatSize;
    }
}
