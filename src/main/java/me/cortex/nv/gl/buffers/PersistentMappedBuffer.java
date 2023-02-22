package me.cortex.nv.gl.buffers;

public class PersistentMappedBuffer extends GlBuffer implements IClientMappedBuffer {
    @Override
    public int id() {
        return 0;
    }

    @Override
    public long clientAddress() {
        return 0;
    }
}
