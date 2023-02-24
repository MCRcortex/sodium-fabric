package me.cortex.nv.gl;

public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    private final int vertexFormatSize;

    public BufferArena(int vertexFormatSize) {
        this.vertexFormatSize = vertexFormatSize;
    }
}
